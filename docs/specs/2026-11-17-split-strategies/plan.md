# Exact and percentage splits: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An expense can be split equally, by exact amounts or by percentages (J4a, J4b, J4c), with the shares always adding up to the total.

**Architecture:** The request carries `participants: [{userId, value?}]`. The controller parses the values into `SplitEntry`s; the service picks `EqualSplit`, `ExactSplit` or `PercentageSplit` (pure domain functions) to turn them into `ExpenseShare`s; `Expense.record` stays the one place that checks the sum (R4) and now answers 422 `SHARES_NOT_TOTAL`. Settlement and notification are unchanged. The form gets a mode choice, one box per ticked member, a live euro preview and validates on submit.

**Tech Stack:** Spring Boot 4.1 / Java 21, JUnit 5 + AssertJ + Mockito, MockMvc + Testcontainers; Next.js 16, React 19, Vitest + Testing Library, Playwright.

**Spec:** `docs/specs/2026-11-17-split-strategies/design.md` (decisions D1 to D12 are cited below; read it first).

## Global Constraints
- Branch `feature/split-strategies` (exists, design committed). Gates on (`git config core.hooksPath .githooks`); never `--no-verify`. Docker Desktop must run for backend tests.
- The contract changes first, **in its own commit** (`contract(expense): ...`); use the `/contract-change` skill for Task 1.
- New error codes in three places at once: `openapi.yaml`, `ErrorCode.java`, `frontend/src/lib/errors.ts` (`03` section 3).
- Names from the diagram and glossary: `paidBy`, `totalAmount`, `splitStrategy`, `participantId`, `owedShare`. New names (`ExactSplit`, `PercentageSplit`, `SplitEntry`) are recorded in `docs/diagram-deltas.md` (Task 6).
- Money: `Money` / `BigDecimal` in Java, never `double`; integer cents in TypeScript, all of it in `lib/money.ts` (`04` section 3). JSON money is a decimal string.
- Rounding (D4): floor each exact share to cents; leftover cents one each to the largest dropped fraction, ties by the order given.
- Commits: `<type>(<scope>): <subject>`, one concern each, trailer `Co-Authored-By: Claude <model> <noreply@anthropic.com>`. Keep line endings (edit tools only; check `git diff --stat`).
- Java: `./mvnw spring-javaformat:apply` before committing. Tests: sentence names, `// given // when // then`, each rule once at the lowest level (`02`).
- Never write regexes through shell heredocs; write them with the edit tools and prove each with a test.

## Review Focus
1. **A percentage typed the German way (`33,33`)** must be accepted and sent as `"33.33"`: pinned in Task 4 (`percentToHundredths`) and Task 5 (posted body).
2. **A member unticked after a value was typed** must not count in the Remaining / Sum line nor be sent: Task 5 `anUntickedMemberNeitherCountsNorIsSent`.
3. **An EXACT expense with a wrong sum into a closed group** must answer `GROUP_NOT_OPEN`, not `SHARES_NOT_TOTAL` (the sum check is last): Task 2 `aWrongSumIsCheckedAfterTheOtherRules`.
4. **A `value` sent with EQUAL, or missing with EXACT** must be a 400, never silently ignored: Task 3 controller test.
5. **Existing EQUAL expenses and their remainder rule** must not change: Task 3 keeps `MallorcaSliceTest.remainderCentsGoToTheFirstParticipantsAndTheSumStaysZero` green unchanged in its assertions; Task 6 e2e still records the hotel equally.

---

### Task 1: Contract

**Files:**
- Modify: `docs/api/openapi.yaml` (the `recordExpense` operation near line 356; schemas `SplitStrategy` line 991, `RecordExpenseRequest` line 1000; the `DomainRule` 422 description line 778; the code enum near line 831)

**Interfaces:**
- Produces: request schema `RecordExpenseRequest { title, totalAmount, date, paidBy, participants: SplitParticipant[] (minItems 1), splitStrategy }`, `SplitParticipant { userId: uuid (required), value?: string }`; error codes `SHARES_NOT_TOTAL`, `PERCENTAGES_NOT_100`; `SPLIT_NOT_SUPPORTED` gone.

- [ ] **Step 1:** Follow `/contract-change`. In `RecordExpenseRequest` replace `participantIds` with `participants` (array of `SplitParticipant`, `minItems: 1`, description: "Distinct members, at least one. `value` is the amount for EXACT, the percentage for PERCENTAGE (at most two decimals), absent for EQUAL (400 otherwise)."). Define `SplitParticipant` with `value` pattern `'^-?\d+(\.\d{1,2})?$'`.
- [ ] **Step 2:** `SplitStrategy` description: "EQUAL divides the total; EXACT takes an amount per participant (they must add up to the total, SHARES_NOT_TOTAL); PERCENTAGE takes a percentage per participant (they must add up to 100, PERCENTAGES_NOT_100). Leftover cents go to the largest dropped fraction, ties by order (R4)." Operation summary: "Record an expense."
- [ ] **Step 3:** Error codes: add `SHARES_NOT_TOTAL` and `PERCENTAGES_NOT_100` to the enum and to the 422 description of `recordExpense`; remove `SPLIT_NOT_SUPPORTED` everywhere (`grep -n SPLIT_NOT_SUPPORTED docs/api/openapi.yaml` must print nothing).
- [ ] **Step 4:** Examples: the request example becomes the design's EXACT farewell dinner (section 6, with the example UUIDs already used in the file); keep the 201 example consistent with it (shares 50.00 / 50.00 / 40.00 / 60.00 / 50.00, `splitStrategy: EXACT`, total 250.00).
- [ ] **Step 5:** Run `npx @redocly/cli lint docs/api/openapi.yaml`. Expected: no errors.
- [ ] **Step 6:** Commit only the contract: `contract(expense): exact and percentage splits with per-participant values`.

### Task 2: Domain: the sum rule in the aggregate, `SplitEntry`, `ExactSplit`, `PercentageSplit`

**Files:**
- Modify: `backend/src/main/java/de/hsh/favs/sharewise/shared/ErrorCode.java` (add `SHARES_NOT_TOTAL(422)`, `PERCENTAGES_NOT_100(422)`; keep `SPLIT_NOT_SUPPORTED` until Task 3)
- Modify: `backend/src/main/java/de/hsh/favs/sharewise/expense/domain/Expense.java:91-139`
- Create: `backend/src/main/java/de/hsh/favs/sharewise/expense/domain/SplitEntry.java`, `ExactSplit.java`, `PercentageSplit.java`
- Modify: `SplitStrategy.java` (Javadoc: drop "Only EQUAL is supported in Ex4")
- Test: `backend/src/test/java/de/hsh/favs/sharewise/expense/domain/ExpenseTest.java` (rewrite the test at line 130), create `ExactSplitTest.java`, `PercentageSplitTest.java`

**Interfaces:**
- Produces: `public record SplitEntry(UserId participantId, BigDecimal value)` (value null for EQUAL); `public static List<ExpenseShare> ExactSplit.split(Money total, List<SplitEntry> entries)`; `public static List<ExpenseShare> PercentageSplit.split(Money total, List<SplitEntry> entries)`; `Expense.record` throws `DomainException(SHARES_NOT_TOTAL)` for a wrong sum.

- [ ] **Step 1: Failing tests in `ExpenseTest`.** Replace `sharesThatDoNotAddUpToTheTotalAreAProgrammingError` with:
  - `sharesThatDoNotAddUpToTheTotalAreRefusedWithSharesNotTotal`: shares 4.99 for ALICE, total 5.00 → `DomainException` with `SHARES_NOT_TOTAL`.
  - `aWrongSumIsCheckedAfterTheOtherRules`: closed group (`new ExpenseFixture().withClosedGroup().groupView()`), shares 4.99, total 5.00 → `GROUP_NOT_OPEN`.
- [ ] **Step 2: Failing tests `ExactSplitTest`:**
  - `typedAmountsBecomeTheSharesInTheGivenOrder`: total 250.00, entries A 50, B 50, C 40, D 60, E 50 (use the fixture's ALICE, BOB, CAROL, DAVID and one new id) → shares equal exactly those, in order.
  - `aZeroOrNegativeAmountIsRefused` (parameterised `"0.00"`, `"-1.00"`) → `AMOUNT_NOT_POSITIVE`.
  - `noEntriesGiveNoShares` → empty list (so `Expense.record` answers `NO_PARTICIPANTS`).
- [ ] **Step 3: Failing tests `PercentageSplitTest`.** One `@ParameterizedTest` `leftoverCentsGoToTheLargestDroppedFraction` over this table (verified by script; copy it as is, it is twinned in Task 4):

  | total | percentages | shares |
  |---|---|---|
  | 10.00 | 33.33, 33.33, 33.34 | 3.33, 3.33, 3.34 |
  | 10.00 | 33.34, 33.33, 33.33 | 3.34, 3.33, 3.33 |
  | 200.00 | 50, 30, 20 | 100.00, 60.00, 40.00 |
  | 10.00 | 12.5, 87.5 | 1.25, 8.75 |
  | 0.50 | 99, 1 | 0.50, 0.00 |
  | 100.00 | 33.33, 33.33, 33.34 | 33.33, 33.33, 33.34 |

  Plus: `percentagesThatDoNotAddUpTo100AreRefused` (50, 49.99) → `PERCENTAGES_NOT_100`; `aZeroOrNegativePercentageIsRefused` (0, -5) → `AMOUNT_NOT_POSITIVE`; `aShareMayRoundToZero` is the 0.50 row (D10, no separate test).
- [ ] **Step 4:** Run `./mvnw -q test -Dtest='ExpenseTest,ExactSplitTest,PercentageSplitTest'` in `backend/`. Expected: compile errors or FAIL.
- [ ] **Step 5: Implement.**
  - `Expense.record`: the sum check stays last, throws `new DomainException(ErrorCode.SHARES_NOT_TOTAL, "The shares add up to " + sum + ", not to " + totalAmount)`. Javadoc: add `SHARES_NOT_TOTAL, R4` at the end of the documented order; remove the `@throws IllegalArgumentException`.
  - `ExactSplit.split`: each value must be positive (`AMOUNT_NOT_POSITIVE`, Javadoc cites R18: a 0.00 recorder would undo it); `new ExpenseShare(id, new Money(value, total.currency()))`. No sum check (D5).
  - `PercentageSplit.split`: each percentage positive (`AMOUNT_NOT_POSITIVE`), sum exactly 100 (`PERCENTAGES_NOT_100`), then:
    ```java
    // exact = total * pct / 100 (exact in BigDecimal, scale 6); floor to cents
    // leftover = total - sum(floors), in cents, always < entries.size()
    // indices sorted by (exact - floor) descending, ties by index ascending;
    // the first `leftover` of them get +0.01 (R4, design D4)
    ```
  - Class Javadoc of each: the rule in one sentence, rule numbers, the codes thrown (`03` section 2).
- [ ] **Step 6:** Run the three tests again. Expected: PASS. Then `./mvnw spring-javaformat:apply`.
- [ ] **Step 7:** Commit: `feat(expense): exact and percentage split rules, sum check in the aggregate`.

### Task 3: Service, command, controller and HTTP tests

**Files:**
- Modify: `backend/src/main/java/de/hsh/favs/sharewise/expense/application/RecordExpenseCommand.java`, `ExpenseService.java:41-61`
- Modify: `backend/src/main/java/de/hsh/favs/sharewise/expense/web/RecordExpenseRequest.java`, `ExpenseController.java:36-43`
- Modify: `ErrorCode.java` (remove `SPLIT_NOT_SUPPORTED`)
- Test: `ExpenseServiceTest.java`, `RecordExpenseCommandFixture.java`, `ExpenseRequests.java`, `ExpenseControllerTest.java`, `ExpenseServiceDatabaseTest.java` (only if it builds a command), `MallorcaSliceTest.java:303-315`

**Interfaces:**
- Consumes: Task 2's `SplitEntry`, `ExactSplit.split`, `PercentageSplit.split`.
- Produces: `RecordExpenseCommand(GroupId groupId, UserId caller, ExpenseTitle title, Money totalAmount, LocalDate date, UserId paidBy, List<SplitEntry> participants, SplitStrategy splitStrategy)`; request record `RecordExpenseRequest(..., @NotNull @Size(min = 1) List<@NotNull @Valid Participant> participants, ...)` with nested `record Participant(@NotNull UUID userId, String value)`.

- [ ] **Step 1: Update the test helpers** (no behaviour yet): `RecordExpenseCommandFixture` holds `List<SplitEntry>` (default the four friends, value null) with `withParticipants(UserId...)` (EQUAL) and `withValues(String... values)` (pairs values with the participants in order); `ExpenseRequests` posts `participants: [{userId, value?}]` and gets `withValues(String... values)`; `MallorcaSliceTest.recordExpense` posts the new shape (values absent, EQUAL).
- [ ] **Step 2: Failing service test.** Replace `aSplitStrategyOtherThanEqualIsRefusedAndNothingIsSaved` with `eachStrategyTurnsTheInputIntoShares` (parameterised): EXACT 900.00 with 300, 200, 200, 200 → published shares 300.00 / 200.00 / 200.00 / 200.00; PERCENTAGE 900.00 with 40, 20, 20, 20 → 360.00 / 180.00 / 180.00 / 180.00. And `aRefusedSplitSavesAndPublishesNothing`: PERCENTAGE 50, 49.99, ... → `PERCENTAGES_NOT_100`, `verifyNoInteractions(expenses, events)`.
- [ ] **Step 3: Failing controller tests** in `ExpenseControllerTest` (replace `aSplitStrategyOtherThanEqualIs422`):
  - `anExactSplitIs201WithTheTypedShares`: alice, bob; 10.00; values 6.00, 4.00 → 201, `$.splitStrategy` EXACT, shares 6.00 / 4.00.
  - `aPercentageSplitIs201AndTheLeftoverCentFollowsTheLargestFraction`: alice, bob, carol; 10.00; 33.33, 33.33, 33.34 → shares 3.33 / 3.33 / 3.34.
  - `exactAmountsThatDoNotAddUpAre422SharesNotTotal` (6.00, 3.00 on 10.00) and `percentagesThatDoNotAddUpAre422` (`PERCENTAGES_NOT_100`); both then list the group's expenses and find none.
  - `aValueWithEqualOrNoValueWithExactOrPercentageIs400` and `aValueThatIsNotANumberWithAtMostTwoDecimalsIs400` (parameterised `"33.333"`, `"abc"`, `"1,5"`) → 400 `BAD_REQUEST`.
- [ ] **Step 4:** Run `./mvnw -q test -Dtest='ExpenseServiceTest,ExpenseControllerTest'`. Expected: FAIL.
- [ ] **Step 5: Implement.**
  - `ExpenseController.recordExpense`: for each participant build `SplitEntry`. EQUAL: `value` must be null. EXACT: `Money.eur(value).amount()`. PERCENTAGE: parse with a private method using the pattern `^-?\d{1,3}(\.\d{1,2})?$` into `BigDecimal`. A wrong shape throws `DomainException(BAD_REQUEST, ...)`.
  - `ExpenseService.recordExpense`: remove the `SPLIT_NOT_SUPPORTED` check; `switch (command.splitStrategy())` → `EqualSplit.split(total, ids)` / `ExactSplit.split(total, entries)` / `PercentageSplit.split(total, entries)`, still after `groups.view`. Javadoc: drop "split equally" and `SPLIT_NOT_SUPPORTED`.
  - Remove `SPLIT_NOT_SUPPORTED` from `ErrorCode`; `grep -rn SPLIT_NOT_SUPPORTED backend/` prints nothing.
- [ ] **Step 6: Slice test** `exactFarewellDinnerBooksTheTypedAmounts` in `MallorcaSliceTest`: group alice, bob, carol; alice pays 250.00 EXACT 100.00 / 100.00 / 50.00 → balances (cents) alice +15000, bob -10000, carol -5000. Extend `recordExpense` with a strategy and values instead of adding a second helper.
- [ ] **Step 7:** Run `./mvnw spring-javaformat:apply` then `./mvnw -q verify`. Expected: BUILD SUCCESS (includes `ModularityTests`, `ConventionTests`, the unchanged remainder slice test).
- [ ] **Step 8:** Commit: `feat(expense): record exact and percentage splits over HTTP`.

### Task 4: Frontend money helpers

**Files:**
- Modify: `frontend/src/lib/money.ts`, `frontend/src/lib/money.test.ts`
- Modify: `frontend/src/components/ExpenseForm.tsx` (only: import `equalShares` from `money.ts` instead of defining it)

**Interfaces:**
- Produces: `equalShares(totalCents: number, count: number): number[]` (moved, unchanged); `percentToHundredths(input: string): number | null` ("33,33" and "33.33" → 3333, "12.5" → 1250, "50" → 5000; null for "33.333", "", "abc", "0", "-5", "1.000"); `percentageShares(totalCents: number, hundredths: number[]): number[]` (D4 rule in integers); `formatPercent(hundredths: number): string` (3333 → "33,33 %", 8000 → "80,00 %").

- [ ] **Step 1: Failing tests** in `money.test.ts`: `describe("percentToHundredths")` with the cases above; `describe("percentageShares")` with `leftoverCentsGoToTheLargestDroppedFraction` over **the same table as Task 2 Step 3**, in cents (comment: "Twin of PercentageSplitTest; change both together"); `describe("formatPercent")`; an `equalShares` case `remainderGoesToTheFirst` (1000 among 3 → 334, 333, 333).
- [ ] **Step 2:** Run `npm test -- money`. Expected: FAIL.
- [ ] **Step 3: Implement** in `money.ts`, integers only (`Math.floor`, `%`, no `parseFloat`). `percentageShares`: `exact_i = totalCents * h_i`, `floor_i = Math.floor(exact_i / 10000)`, `rest_i = exact_i % 10000`; leftover cents to indices sorted by `rest` descending, ties by index. Move `equalShares` with its comment.
- [ ] **Step 4:** Run `npm test -- money`, then `npm run lint && npm run typecheck`. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(frontend): money helpers for percentage splits`.

### Task 5: Form, error messages, generated types

**Files:**
- Modify: `frontend/src/api/schema.d.ts` (only via `npm run gen:api`)
- Modify: `frontend/src/components/ExpenseForm.tsx`, `frontend/src/lib/errors.ts`
- Test: `frontend/src/components/Expenses.test.tsx`

**Interfaces:**
- Consumes: Task 1 contract types; Task 4 helpers.

- [ ] **Step 1:** `npm run gen:api`. Expected: `schema.d.ts` has `participants` and no `participantIds`; `npm run typecheck` now fails in `ExpenseForm.tsx` and `Expenses.test.tsx`.
- [ ] **Step 2: Failing component tests** in `Expenses.test.tsx` (`describe("ExpenseForm")`):
  - `submitPostsExpenseAndRefreshesList` (update): body has `participants: [{userId: "u1"}, {userId: "u2"}]`, `splitStrategy: "EQUAL"`, no `value`.
  - `anExactSplitPostsTheTypedAmounts`: choose "Exact amounts", 10 total, `Amount for Alice` 6, `Amount for Bob` 4 → body values `"6.00"`, `"4.00"`, `splitStrategy: "EXACT"`; `Remaining: 0,00 €` shown.
  - `aPercentageSplitPreviewsTheSharesAndPostsThePercentages`: 10 total, 33,33 / 33,33 / 33,34 → preview 3,33 € / 3,33 € / 3,34 €, `Sum: 100,00 %`, body values `"33.33"`, `"33.33"`, `"33.34"`.
  - `amountsThatDoNotAddUpShowAMessageAndSendNothing`: 10 total, 6 and 3 → "The amounts must add up to 10,00 €." and `api` not called. Same for percentages: "The percentages must add up to 100 %."
  - `anUntickedMemberNeitherCountsNorIsSent` (Review Focus 2): type 4 for Bob, untick Bob → Remaining reflects Alice only; body has only Alice.
  - `switchingTheModeClearsTheTypedValues` (F4).
  - `serverErrorCodeShownAsMessage`: add `SHARES_NOT_TOTAL` → "The amounts must add up to the total."
- [ ] **Step 3:** Run `npm test -- Expenses`. Expected: FAIL.
- [ ] **Step 4: Implement `ExpenseForm`.**
  - A radio group labelled "Split" with "Equally", "Exact amounts", "Percentages" (state `splitStrategy`, default EQUAL); the fieldset legend becomes "Split between".
  - EXACT / PERCENTAGE: next to each ticked member an input labelled `Amount for <name>` / `Percentage for <name>`, `required`, `inputMode="decimal"`; values in state keyed by `userId`; switching the mode clears them.
  - Live line under the list: EXACT `Remaining: <formatEuro(total - sum)>`, PERCENTAGE `Sum: <formatPercent(sum)>`; the euro preview uses `equalShares` / typed cents / `percentageShares`. Only ticked members count.
  - On submit (existing `submit`): parse each box with `eurosToCents` / `percentToHundredths`; a box that does not parse → "Please enter a valid amount for <name>." / "Please enter a valid percentage for <name>."; a sum that does not match → the messages above; nothing sent. Body: `participants` of ticked members, `value` as `centsToAmount(cents)` or `centsToAmount(hundredths)` (both give the two-decimal string), absent for EQUAL.
  - Colours only through the theme tokens (`04` section 5); check both colour modes at phone width.
- [ ] **Step 5: `errors.ts`:** add `SHARES_NOT_TOTAL: "The amounts must add up to the total."`, `PERCENTAGES_NOT_100: "The percentages must add up to 100 %."`; `AMOUNT_NOT_POSITIVE` becomes "Amounts and percentages must be greater than zero."; remove `SPLIT_NOT_SUPPORTED`.
- [ ] **Step 6:** Run `npm run lint && npm run typecheck && npm test && npm run build`. Expected: all green (includes `themeRules.test.ts`).
- [ ] **Step 7:** Commit: `feat(frontend): exact and percentage splits in the expense form`.

### Task 6: Journey test and docs

**Files:**
- Modify: `frontend/e2e/mallorca.spec.ts:94-102`
- Modify: `docs/domain/ShareWise_Requirements.md:118`, `docs/domain/Ubiquitous_Language.md:94`, `docs/notes/2026-11-04-money-flow.md` (section 12), `docs/diagram-deltas.md` (known gaps line 45 and a new delta row), `docs/specs/2026-11-17-split-strategies/design.md` (status line), `RUNNING.md` (only if `grep -n "EQUAL\|equal split" RUNNING.md` finds a statement that changed)

- [ ] **Step 1: e2e.** Bob records the dinner (180) as **Exact amounts** 60 / 60 / 60 instead of equally (add an `addExactExpense` helper next to `addExpense`). The balances (+540, -180, -360) stay the same, so the rest of the journey is unchanged.
- [ ] **Step 2:** With backend and Postgres running, `npm run e2e`. Expected: both tests pass. (CI runs it against the built images too.)
- [ ] **Step 3: Docs (D11).**
  - R4: "The shares add up exactly to the amount. With a percentage split, the percentages add up to 100 %. Remainder cents go to the participants whose exact share lost the most to rounding; on a tie, to the first in the order given (so an equal split gives them to the first participants)."
  - Glossary "Equal split": "remainder cents per R4"; add rows "Exact split" and "Percentage split" in the same table.
  - `money-flow.md` section 12: one paragraph that percentage splits use the largest leftover (D4), which gives today's result for equal splits.
  - `diagram-deltas.md`: remove "the `EXACT` and `PERCENTAGE` split strategies" from known gaps; add a delta row for `ExactSplit`, `PercentageSplit`, `SplitEntry` (domain functions and input record, not drawn).
  - Design status: "built".
  - `grep -rn "SPLIT_NOT_SUPPORTED\|participantIds\|Only EQUAL" --include=*.md --include=*.yaml --include=*.ts --include=*.tsx --include=*.java .` (excluding `docs/specs/` and `docs/review/`) prints nothing.
- [ ] **Step 4:** Commit: `test(e2e): the journey records the dinner as an exact split` and `docs(expense): R4 and glossary for exact and percentage splits` (two commits).
- [ ] **Step 5:** Hand over to `/review-branch`, then `/review-doc`, then the pull request (CLAUDE.md "Before merging a branch").
