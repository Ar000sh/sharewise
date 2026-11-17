# Design: exact and percentage splits (J4b, J4c)

**Date:** 17.11.26 · **Type:** feature · **Branch:** `feature/split-strategies` · **Status:** built · **Checked by:** `/edge-case-probe` plan mode and `/design-review` (17.11., section 9)

## 1. Intent
J4 says an expense is split **equal, exact or percentage**. Only EQUAL works today: the enum `SplitStrategy {EQUAL, EXACT, PERCENTAGE}` and the diagram already name all three, but the service refuses the other two (`SPLIT_NOT_SUPPORTED`) and the form hardcodes `"EQUAL"`. Mallorca step 11 (farewell dinner, exact: A 50, B 50, C 40, D 60, E 50 = 250) cannot run until EXACT exists. Why now: the P3 demo (15.12) and a complete J4.

Concept practised: the **aggregate keeps the one invariant** (R4: the shares add up to the total) in one place, whatever produced the shares; a small domain function per strategy only turns the recorder's input into `ExpenseShare`s.

## 2. What exists *(verified in the code)*
| Where | Today |
|---|---|
| `Expense.record(view, ..., shares, splitStrategy, now)` (`Expense.java:103`) | Takes finished shares. Checks in a documented order (open, payer, recorder, participants, R18, total positive), then the sum: a wrong sum is an `IllegalArgumentException`, a 500, "the split was calculated wrongly" (`Expense.java:134-139`, pinned by `ExpenseTest:130`). Only the shares are stored (`expense_shares`), plus the strategy label. |
| `ExpenseService.recordExpense` | Throws `SPLIT_NOT_SUPPORTED` for anything but EQUAL, then `EqualSplit.split(total, participants)` **before** `Expense.record`. |
| `RecordExpenseRequest` / `RecordExpenseCommand` | `participantIds: List<UUID>` / `participants: List<UserId>`, no per-person value. The controller turns `"250.00"` into `Money` with `Money.eur` (400 on a bad format). |
| Database (V302) | `expense_shares.owed_share >= 0`, `expenses.total_amount > 0`. No migration needed. |
| `docs/api/openapi.yaml` | `recordExpense` summary "Only splitStrategy EQUAL is supported in Ex4"; `participantIds` "Order decides who gets the remainder cents"; `SPLIT_NOT_SUPPORTED` in the 422 text and the code enum. |
| `ExpenseForm.tsx` | One mode. Already previews each share for EQUAL with its own `equalShares()` ("like the backend"); validates on submit (`04` section 5). `04` section 3 puts share previews in `lib/money.ts`. |
| Settlement, notification | See only `ExpenseShare`s through `ExpenseRecorded`: **no change**. |

## 3. Goals, non-goals, anti-goals
**Goals**
1. `EXACT`: a positive amount per participant; the amounts add up to the total (R4).
2. `PERCENTAGE`: a positive percentage per participant with at most two decimals; the percentages add up to 100 %; the euro shares add up to the total exactly, and the leftover cents follow what the recorder typed (D4).
3. The form offers the three modes with a live share preview and a running Remaining / Sum line, and validates on submit.
4. A wrong sum is a **422 with its own code**, never a 500.

**Non-goals (parked, with the trigger)**
- Storing the typed percentage (user, 17.11.): the shares are the truth. Trigger: J5 "correct expense", if the pre-filled percentage looks wrong after rounding.
- A server-side preview endpoint: the browser computes the preview (D8).
- Sub-cent shares (user, 17.11.): debts could never reach exactly 0 (blocks R11, L4, L5), and `Money` would change in every module.
- Correct and remove expense, leave/remove member, group lifecycle: separate features.

**Anti-goals** (a failure even if it works)
- A second source of truth for a share.
- A balance that does not sum to zero because a split lost or invented a cent.
- A preview that differs from what the server saves.
- A result that contradicts what the recorder typed (the bigger percentage paying less).

## 4. Approaches considered
| Question | Options | Chosen (user, 17.11.) |
|---|---|---|
| Request shape | **A** `participants: [{userId, value?}]` replaces `participantIds`; **B** keep `participantIds`, add a second `values` list | **A**: each person once; B allows two lists that disagree. Cost: the old shape breaks, so frontend, tests and e2e change (nobody outside the project calls the API). |
| Preview of shares | **X** real euro shares in every mode; **Y** only the Remaining / Sum line | **X**: EQUAL already previews with a copy of the backend rule; PERCENTAGE without it would be the odd mode. Cost: the rounding exists in Java and TypeScript, pinned by one table of cases. |
| Percentage precision | whole numbers; two decimals | **two decimals** (33.33, 12.5). |
| Store the typed percentage | yes; no | **no**. |
| Who gets the leftover cent | **first in the list** (R4 today); **largest leftover fraction**, ties by list order | **largest leftover fraction**: the recorder typed the percentages, so the result must match them. Same result as today for EQUAL (all fractions equal, ties by order; checked by script), so `EqualSplit` does not change. |
| Where the sum rule lives (from the design review) | in each strategy, plus the old 500 guard in the aggregate; **only in the aggregate, as a 422** | **only in the aggregate** (section 9). |
| Schema validation of the sum | in `openapi.yaml`; in the aggregate | **aggregate**: JSON Schema checks one field at a time and cannot add values up; nothing validates requests against the contract at runtime (the frontend only generates types from it). The contract still describes the per-field rules. |

## 5. Decisions
| # | Decision | Why |
|---|---|---|
| D1 | **Wire shape (A).** `participants: [{ userId: uuid, value?: string }]`, at least one (`minItems: 1`: an empty list is a 400 at the HTTP layer, built that way in the contract); `splitStrategy` unchanged. `value` is an amount (`"50.00"`) for EXACT, a percentage (`"33.33"`, point, at most two decimals) for PERCENTAGE and **absent** for EQUAL. | One place per person. |
| D2 | **Inside the backend.** The controller turns the request into `RecordExpenseCommand(..., List<SplitEntry> participants, splitStrategy)` with `SplitEntry(UserId participantId, BigDecimal value)` (value null for EQUAL). The controller parses each value (an amount with `Money.eur`, a percentage with a two-decimal parse) and answers **400 `BAD_REQUEST`** for a value that is not a number, has more than two decimals, is missing for EXACT/PERCENTAGE or present for EQUAL. (call) | Shape errors are request validation (`03` section 3); the same place already parses `totalAmount`. |
| D3 | **Strategies** are domain functions next to `EqualSplit`, picked by a `switch` in the service: `ExactSplit.split(total, entries)` checks every amount is positive (`AMOUNT_NOT_POSITIVE`; a 0.00 recorder would undo R18) and maps to shares, **without a sum check**. `PercentageSplit.split(total, entries)` checks every percentage is positive (`AMOUNT_NOT_POSITIVE`) and that they sum to 100 (`PERCENTAGES_NOT_100`), then rounds per D4 in `BigDecimal`. `EqualSplit` unchanged. (call) | Same shape as `EqualSplit`; no new abstraction (`03` section 7). |
| D4 | **Rounding (R4, new wording).** Each exact share is `total × percentage / 100` in `BigDecimal` (no overflow), rounded down to cents. The leftover cents (always fewer than the participants) go one each to the participants whose exact share lost the most to rounding; on a tie, to the first in the order given. Verified by script: 10.00 at 33.33 / 33.33 / 33.34 gives **3.33 / 3.33 / 3.34**; at 33.34 / 33.33 / 33.33 gives 3.34 / 3.33 / 3.33; 200.00 at 50 / 30 / 20 gives 100.00 / 60.00 / 40.00; 10.00 at 12.5 / 87.5 gives 1.25 / 8.75; 0.50 at 99 / 1 gives 0.50 / 0.00 (D10). For EQUAL every fraction is the same, so this is today's rule. | Matches what the recorder typed (user, 17.11.). |
| D5 | **The sum rule lives once, in `Expense.record`**: a wrong sum becomes `DomainException(SHARES_NOT_TOTAL)`, a 422, as the last check of the documented order (after `AMOUNT_NOT_POSITIVE`). It replaces the `IllegalArgumentException`; `ExpenseTest:130` is rewritten, not added. | One place per rule; the ordinary errors (group not open, not a member) come first for every strategy (design review). |
| D6 | **New error codes:** `SHARES_NOT_TOTAL` (422, from the aggregate) and `PERCENTAGES_NOT_100` (422, from `PercentageSplit`), in the contract, `ErrorCode` and `frontend/src/lib/errors.ts`. A zero or negative value reuses `AMOUNT_NOT_POSITIVE`; its frontend sentence becomes neutral ("must be greater than zero"). (call) | `03` section 3: every new code in all three places. |
| D7 | **`SPLIT_NOT_SUPPORTED` is removed** from `ErrorCode`, the contract (422 text and enum), `errors.ts` and the service. | Dead once all three are built; grep for the old term. |
| D8 | **Form.** A mode choice (equal / exact / percentage) in the existing split section. EXACT and PERCENTAGE: one box per ticked member, the recorder always ticked (R18). Live, below the list: the euro share per person and `Remaining: 10.00` (EXACT) or `Sum: 80 %` (PERCENTAGE). **Validation on submit**, like the amount field today: the boxes are `required` (the browser marks an empty one), the values are parsed through `lib/money.ts`, and a wrong format or a sum that does not match shows the `ErrorBanner` and sends nothing. Only ticked members count and are sent. Switching the mode clears the typed values. (call) | `04` section 5 (validate on submit); the server stays the final check. |
| D9 | **Money helpers in `lib/money.ts`:** `equalShares` moves there from `ExpenseForm.tsx`, plus a percentage parser (comma or point, at most two decimals, no floating point) and `percentageShares` (D4 in integer cents). The preview may be wrong above about 9 billion euro (JavaScript safe integers): accepted, the server is the truth. (call) | `04` section 3: money logic only in `money.ts`. |
| D10 | **A share below one cent rounds to 0.00 and is allowed** (user, 17.11.): the person stays a participant, as with the equal split today (0.02 among 3 gives 0.01 / 0.01 / 0.00). Never from a total of 100.00 upwards. Rejected: refusing the expense, dropping the person (input lost, can break R18), sub-cent shares. Revisit if users ask. | Harmless; no new rule. |
| D11 | **Docs with the code:** R4 in `ShareWise_Requirements.md` and the glossary row "Equal split" (`Ubiquitous_Language.md`) get the D4 wording; `money-flow.md` section 12 gets a line that percentages use the largest leftover; `diagram-deltas.md` lists `ExactSplit`, `PercentageSplit`, `SplitEntry` (domain functions and an input record, not in the diagram); the `participantIds` description in the contract goes. | Docs change with the code (CLAUDE.md). |
| D12 | **Order of commits:** this design; `contract(expense)` alone; backend (aggregate, strategies, service, controller, error codes, tests); frontend (form, `money.ts`, `errors.ts`, tests); docs (D11). | `03` section 6. |

## 6. Wire examples
```json
{ "title": "Farewell dinner", "totalAmount": "250.00", "date": "2027-06-05", "paidBy": "<alice>",
  "splitStrategy": "EXACT",
  "participants": [ {"userId": "<alice>", "value": "50.00"}, {"userId": "<bob>", "value": "50.00"},
                    {"userId": "<carol>", "value": "40.00"}, {"userId": "<david>", "value": "60.00"},
                    {"userId": "<eve>", "value": "50.00"} ] }
```
```json
{ "splitStrategy": "PERCENTAGE", "totalAmount": "200.00",
  "participants": [ {"userId": "<alice>", "value": "50"}, {"userId": "<bob>", "value": "30"}, {"userId": "<carol>", "value": "20"} ] }
```
EQUAL: `"participants": [ {"userId": "<alice>"}, {"userId": "<bob>"} ]`.

## 7. Tests (each rule once, at the lowest level)
| Rule | Level |
|---|---|
| Wrong sum gives `SHARES_NOT_TOTAL` after the other checks (replaces `ExpenseTest:130`) | domain unit `ExpenseTest` |
| EXACT: amounts become shares; zero or negative gives `AMOUNT_NOT_POSITIVE` | domain unit `ExactSplitTest` |
| PERCENTAGE: the D4 table (with the 3.33 / 3.33 / 3.34 row, a tie, 12.5, the 0.00 share); not 100 gives `PERCENTAGES_NOT_100`; zero gives `AMOUNT_NOT_POSITIVE` | domain unit `PercentageSplitTest` |
| The same D4 table in TypeScript, with a comment naming its Java twin (it makes drift visible; it does not prevent it) | Vitest `money.test.ts` |
| Percentage parser: `33,33`, `33.33`, `12.5`, `33.333` refused | Vitest `money.test.ts` |
| The service picks the strategy; a refused split saves and publishes nothing | service `ExpenseServiceTest` |
| 400 shapes (D2) and 422 codes become the right status and `code` | controller `ExpenseControllerTest` |
| Mallorca step 11: the exact farewell dinner and its balances | slice `MallorcaSliceTest` |
| Form: mode switch clears values; an unticked member does not count and is not sent; the Remaining / Sum line; a wrong sum on submit shows the banner and posts nothing; the posted body | component test of `ExpenseForm` |
| One journey: record an exact expense and see the balances | e2e (extend the existing expense journey) |

Existing tests that post `participantIds` change to the new shape (`ExpenseControllerTest`, `ExpenseRequests`, `Expenses.test.tsx`, e2e). No random property test: the table and the aggregate's sum check cover it (user, 17.11.).

## 8. Failure modes
From `/edge-case-probe` plan mode, updated after the design review. Only cases reachable through the real app.

| # | Case | Who, how often | What stops it | Mark |
|---|---|---|---|---|
| F1 | Preview and server disagree by a cent (the TypeScript and Java rounding differ) | Through our own mistake while building; then every percentage user | The same D4 table in both test suites | **test** (merged with F2) |
| F2 | Rounding loses or invents a cent | Through our own mistake only | D4 table; the aggregate refuses a wrong sum (D5) | **test** (merged with F1) |
| F3 | Carol is unticked after a value was typed for her, and the value still counts or is sent | Common while filling the form | Only ticked members count and are sent (D8) | **test** (component) |
| F4 | EXACT amounts switched to PERCENTAGE: "50.00" read as 50 % | Occasional | Switching the mode clears the values (D8) | **test** (component) |
| F5 | A percentage typed as `33,33` | Common | Parser accepts comma or point (D9) | **test** (unit) |
| F6 | A huge total overflows the percentage arithmetic | Only a 13-digit amount | `BigDecimal` in `PercentageSplit` (D4): cannot happen | handled by design |
| F7 | EXACT answers a different error than EQUAL for a closed group or a non-member | A hand-made request | The sum check runs last, in the aggregate (D5) | handled by design |
| F8 | The same person twice, or an empty participant list | A hand-made request | An empty list is a 400 at the HTTP layer (`minItems: 1`, built that way). `NO_PARTICIPANTS` stays the domain answer: `Expense.record` refuses duplicates and an empty list from a direct domain call with it (422) | handled by design |
| F9 | Old frontend against new backend for a moment during a deploy | The Ex8 deploy step only | Both are released together from one pull request | accepted |
| F10 | A share below one cent rounds to 0.00 | Tiny totals or tiny percentages | D10 | accepted (user) |
| F11 | The bigger percentage pays the smaller share | Every percentage split with a leftover cent | Largest-leftover rule (D4) | handled by design (user) |

Held up without a row: concurrency (no new writer), events (`ExpenseRecorded` keeps its shape), authorization and lifecycle (unchanged checks), double submit (the form's `busy` flag), old expenses (stored, not recomputed).

## 9. Design review (17.11.) and what changed
An independent reviewer (fresh agent, no access to our discussion) found the design mostly inherent, and one placement mistake: the sum rule was in `ExactSplit` as a 422 **and** in `Expense.record` as a 500 guard. Once users type the amounts, a wrong sum is a business error of the aggregate itself. Moving it there (D5) removed the old D6 (two checks), made F7 disappear (the error order no longer depends on the strategy), and removed F6's guard and test (`BigDecimal` instead of `long`). It also pointed out that the design did not say what the backend passes on internally (now D2), that the new names must be listed as diagram deltas (D11), and that the person who typed the largest percentage paid the smallest share, which the user decided to fix (D4). Rejected from the review: handing the split input to the aggregate as a sealed type (bigger signature and fixtures for no gain now; trigger: J5 "correct expense").

## 10. Left to the builder
Names of private helpers, exact message texts, the layout of the per-person boxes at phone width (both colour modes, `04` section 5).
