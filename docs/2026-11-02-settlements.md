# Settlements (package 1) Implementation Plan

> **Historical (04.11.26):** the settle-up inbox and badge this plan builds were replaced by the bell; see `2026-11-04-settlements-inbox-design.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Members can see a minimum-payments settlement plan, record a settlement payment, have the creditor confirm or reject it, and have the creditor waive a debt; only confirmed payments and waivers change balances.

**Architecture:** `SettlementPayment` is a new aggregate in the `settlement` module next to `GroupLedger`. Payments are checked against a live plan projection plus per-person caps under the ledger lock; confirmed payments and waivers are booked into the ledger asynchronously by their events (idempotent). Two neutral events in `shared.events` tell `group` when a balance is zero, so `group` never depends on `settlement`.

**Tech Stack:** Spring Boot modulith (Java, JPA, Flyway, Spring Modulith events, ArchUnit), Postgres 17 (Testcontainers), Next.js + TypeScript (Vitest, Playwright), OpenAPI contract-first.

**Spec:** `docs/2026-11-02-settlements-design.md` (decisions D1 to D14, failure modes in section 8, review findings in 8b). Package 2 (correct and remove expense, ledger reversals) gets its own plan after this branch is merged; the group lifecycle and auto-archive (L5) are not in this plan.

## Global Constraints
- Names come from the domain diagram: `SettlementPayment`, `PaymentStatus` (PENDING, CONFIRMED, REJECTED, WAIVED), `SettlementPaymentId`, `DirectedDebt(fromDebtor, toCreditor, amount)`, `SettlementPlan`, events `SettlementPaymentRecorded`, `SettlementPaymentConfirmed`, `SettlementPaymentRejected`, `DebtWaived`, `MemberBalanceStatusChanged`, `AllBalancesSettled`, ledger types `PAYMENT_SENT`, `PAYMENT_RECEIVED`, `DEBT_WAIVED`. Always "settlement payment", never "payment" alone in code names and docs.
- Layout per module: `api/ domain/ application/ persistence/ web/`; root methods are named after the diagram operation, return their event (or a small result record), never publish, never call another module, take `Instant now`; services are thin (load, call one root method, save, publish).
- `settlement` may depend only on `account::api, group::api, expense::api, shared` (`settlement/package-info.java`). `group` stays on `account::api, shared`. No cycle.
- Events end with `Instant occurredAt`, carry ids and value objects only. Listeners use `@ApplicationModuleListener` and are idempotent.
- Money is `shared.Money` (EUR, 2 decimals). Money in docs and tests is verified by arithmetic or a script first.
- Contract first, in its own commit; new error codes in all three places: `docs/api/openapi.yaml`, `shared/ErrorCode.java`, `frontend/src/lib/errors.ts`. Regenerate types with `npm run gen:api`, never edit `schema.d.ts`.
- Migrations: next free version is `V403` in `backend/src/main/resources/db/migration/settlement/`; plural snake_case tables; no foreign keys to other modules' tables.
- Tests per `docs/conventions/02_TESTING.md`: sentence names, `// given // when // then`, `*Fixture` classes, real sign-up for integration tests, Awaitility for event results, no `Thread.sleep`. A rejected call is tested to leave nothing behind.
- Commit format `<type>(<scope>): <subject>`, one concern each, trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Never `--no-verify`. Gates: `cd backend && ./mvnw verify` (Docker running), `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`, `npx @redocly/cli lint docs/api/openapi.yaml`.

## Review Focus
1. Amount with more than 2 decimals, an exponent (`1e3`), a string or a negative number in a request: `400 BAD_REQUEST` or `422 AMOUNT_NOT_POSITIVE`, never a 500 (Task 7).
2. The plan changes between the user viewing it and sending the payment (edge vanished): `422 PAYMENT_EXCEEDS_OPEN_DEBT`, nothing saved (Task 4).
3. Confirm or reject by the debtor, by a member who is neither party, by a non-member, or with a payment id of another group: 403 `NOT_THE_CREDITOR` for a member of the group, 404 `SETTLEMENT_PAYMENT_NOT_FOUND` otherwise (Task 7).
4. Confirm twice, confirm after reject, confirm and reject at once: second call is `409 PAYMENT_NOT_PENDING`, exactly one pair of ledger entries (Tasks 4, 5).
5. A group where everyone is at zero, or with one member: empty plan, record refused with `PAYMENT_EXCEEDS_OPEN_DEBT`, no crash (Tasks 2, 7).

---

### Task 1: Branch, design doc, contract (two commits)

**Files:**
- Modify: `docs/api/openapi.yaml`
- Commit as is: `docs/2026-11-02-settlements-design.md`, this plan

**Interfaces:**
- Produces (JSON field names, final): 
  - `GET /groups/{groupId}/settlement-plan` returns `[{ fromDebtor, fromDebtorName, toCreditor, toCreditorName, amount, pendingAmount }]` (user ids as uuid, amounts as decimal strings, `pendingAmount` = reserved on that pair).
  - `POST /groups/{groupId}/settlement-payments` body `{ toCreditor, amount, note? }` returns `201 SettlementPayment`.
  - `GET /groups/{groupId}/settlement-payments` returns `[SettlementPayment]`, newest first.
  - `POST /settlement-payments/{paymentId}/confirm` returns `200 SettlementPayment`; `POST /settlement-payments/{paymentId}/reject` body `{ note }` returns `200 SettlementPayment`.
  - `POST /groups/{groupId}/debt-waivers` body `{ fromDebtor, amount, note }` returns `201 SettlementPayment` with status `WAIVED`.
  - `GET /me/settlement-payments?status=PENDING&role=creditor` returns `[SettlementPayment]` across the caller's groups.
  - `SettlementPayment` = `{ id, groupId, fromDebtor, fromDebtorName, toCreditor, toCreditorName, amount, status, note, createdAt, resolvedAt }`.
  - New `ErrorCode` values: `PAYMENT_EXCEEDS_OPEN_DEBT` (422), `SETTLEMENT_PAYMENT_NOT_FOUND` (404), `PAYMENT_NOT_PENDING` (409), `NOT_THE_CREDITOR` (403). `GROUP_NOT_OPEN` description is extended to "group is archived" for settlement calls.

- [ ] **Step 1:** `git status` is clean on `feature/settlements`; commit the design doc and this plan: `docs(settlement): design and plan for settlement payments`.
- [ ] **Step 2:** Run `/contract-change`; add the paths, schemas, the four error codes (with `Problem` examples) and the `settlement` tag entries to `docs/api/openapi.yaml`. Every path lists 400/401/403/404 (and 422/409 where an error code above applies).
- [ ] **Step 3:** `npx @redocly/cli lint docs/api/openapi.yaml` → no errors.
- [ ] **Step 4:** Commit the contract alone: `feat(contract): settlement plan, settlement payments and debt waivers`. Then in `frontend/`: `npm run gen:api` and commit `chore(frontend): regenerate API types` (the commit gate forbids mixing the contract with code).

### Task 2: Settlement plan (domain)

**Files:**
- Create: `settlement/domain/DirectedDebt.java`, `settlement/domain/SettlementPlan.java`
- Modify: `settlement/domain/GroupLedger.java` (add `projectSimplifiedPlan`)
- Test: `settlement/domain/SettlementPlanTest.java`, extend `GroupLedgerTest`

**Interfaces:**
- Produces: `record DirectedDebt(UserId fromDebtor, UserId toCreditor, Money amount)`; `record SettlementPlan(List<DirectedDebt> simplifiedDebts)` with `static SettlementPlan of(List<MemberBalance> balances)` and `Money edge(UserId debtor, UserId creditor)` (zero when absent); `GroupLedger#projectSimplifiedPlan(): SettlementPlan` = `SettlementPlan.of(projectBalances())`.
- Consumes: `settlement.api.MemberBalance(UserId, Money)`.

- [ ] **Step 1: Write the failing tests** in `SettlementPlanTest` (plain JUnit + AssertJ):
  - `mallorcaBalancesGiveThreePaymentsToAlice`: balances Alice +630.00, Bob -90.00, Carol -270.00, David -270.00 → edges Carol→Alice 270.00, David→Alice 270.00, Bob→Alice 90.00 (sum of edges 630.00; the numbers add up: 270+270+90).
  - `twoIndependentPairsAreSettledSeparatelyNotThroughOnePerson`: A +10, B -10, C +20, D -20 → exactly 2 payments (the greedy-only result would also be 2; add a case where greedy gives 3 and the exact answer 2: A +5, B +5, C -5, D -5, E +3, F -3 → 3 payments, never 5).
  - `theNumberOfPaymentsIsNeverMoreThanPeopleMinusSubgroups` (property over random zero-sum balances up to 8 people, fixed seed).
  - `tiesAreBrokenByUserIdSoTwoCallsGiveTheSamePlan`.
  - `everyoneAtZeroOrAnEmptyListGivesAnEmptyPlan`.
  - `theSumOfEdgesEqualsTheSumOfPositiveBalancesAndNoBalanceIsChanged`.
  - `moreThanSixteenPeopleFallBackToGreedyAndStillSettleEveryone`.
  - `edgeIsZeroForAPairThatIsNotInThePlan`.
- [ ] **Step 2:** `cd backend && ./mvnw -q test -Dtest=SettlementPlanTest` → FAIL (classes missing).
- [ ] **Step 3: Implement** `SettlementPlan.of`: drop zero balances; with up to 16 non-zero people, find the largest number of zero-sum subgroups by bitmask DP (`best[mask] = max over i in mask of best[mask without i]`, plus 1 when the balances of `mask` sum to zero; keep the smallest-index choice on ties so it is deterministic), then settle each subgroup greedily (largest debtor pays largest creditor, ties by user id ascending, until all are zero); above 16 people run the same greedy over everybody. Javadoc cites R15 and states the greedy limit. Order of edges: by creditor id, then debtor id.
- [ ] **Step 4:** `./mvnw -q test -Dtest='SettlementPlanTest,GroupLedgerTest'` → PASS.
- [ ] **Step 5:** `./mvnw spring-javaformat:apply`, commit `feat(settlement): projectSimplifiedPlan with the minimum number of payments (R15)`.

### Task 3: SettlementPayment aggregate, events, error codes

**Files:**
- Create: `settlement/api/SettlementPaymentId.java`, `SettlementPaymentRecorded.java`, `SettlementPaymentConfirmed.java`, `SettlementPaymentRejected.java`, `DebtWaived.java` (all in `settlement/api`); `settlement/domain/PaymentStatus.java`, `settlement/domain/SettlementPayment.java`
- Modify: `shared/ErrorCode.java` (four codes), `frontend/src/lib/errors.ts` (four messages) and `errors.test.ts`
- Test: `settlement/domain/SettlementPaymentTest.java`, `settlement/domain/SettlementPaymentFixture.java`

**Interfaces:**
- Produces: `record SettlementPaymentId(UUID value)`; each of the four events is a record `(SettlementPaymentId paymentId, GroupId groupId, UserId fromDebtor, UserId toCreditor, Money amount, Instant occurredAt)`.
- Produces on `SettlementPayment` (JPA entity `settlement_payments`, optimistic `@Version`):
  - `static Recorded record(GroupId, UserId fromDebtor, UserId toCreditor, Money amount, Money openDebt, String note, Instant now)` with `record Recorded(SettlementPayment payment, SettlementPaymentRecorded event)`; throws `AMOUNT_NOT_POSITIVE`, `PAYMENT_EXCEEDS_OPEN_DEBT` (amount above `openDebt`), `BAD_REQUEST` (same person, note over 500 characters).
  - `SettlementPaymentConfirmed confirm(UserId caller, Instant now)`: `NOT_THE_CREDITOR` if caller is not `toCreditor`, `PAYMENT_NOT_PENDING` unless PENDING.
  - `SettlementPaymentRejected reject(UserId caller, String note, Instant now)`: same checks, blank or over-500 note is `BAD_REQUEST`.
  - `static Waived waive(GroupId, UserId creditor, UserId fromDebtor, Money amount, Money openDebt, String note, Instant now)` with `record Waived(SettlementPayment payment, DebtWaived event)`; status WAIVED at creation; blank note is `BAD_REQUEST`; same amount rules as `record`.
  - Accessors: `paymentId()`, `groupId()`, `fromDebtor()`, `toCreditor()`, `amount()`, `status()`, `note()`, `createdAt()`, `resolvedAt()`.

- [ ] **Step 1: Write the failing tests** in `SettlementPaymentTest` (each rule R16, R17 once, including the rejected case): `recordedPaymentIsPendingAndHoldsTheAmount`, `amountEqualToOpenDebtIsAllowed`, `amountOneCentAboveOpenDebtIsRejectedAndNothingIsCreated`, `zeroAndNegativeAmountsAreNotPositive`, `payingYourselfIsRefused`, `onlyTheCreditorCanConfirm`, `confirmingTwiceIsPaymentNotPending`, `rejectNeedsANoteAndSetsRejected`, `aRejectedPaymentCannotBeConfirmed`, `waiveCreatesAFinishedWaivedPaymentWithoutConfirmation`, `waiveAboveOpenDebtIsRejected`, `eventsCarryIdsAmountAndTime`. `SettlementPaymentFixture` has valid defaults and `withX` methods and builds through `record`.
- [ ] **Step 2:** `./mvnw -q test -Dtest=SettlementPaymentTest` → FAIL.
- [ ] **Step 3:** Implement the types above. Add the four `ErrorCode` values with the statuses from Task 1 and the four texts to `errors.ts` ("You can only pay up to what you owe.", "This settlement payment does not exist.", "This settlement payment was already answered.", "Only the person who is owed can do this."), plus a line each in `errors.test.ts`.
- [ ] **Step 4:** `./mvnw -q test -Dtest='SettlementPaymentTest'` and `cd ../frontend && npm test -- errors` → PASS.
- [ ] **Step 5:** Format, commit `feat(settlement): SettlementPayment aggregate with record, confirm, reject and waive (R16, R17)`.

### Task 4: Persistence, open debt and the settlement payment service

**Files:**
- Create: `db/migration/settlement/V403__settlement_payments.sql`, `settlement/persistence/SettlementPaymentRepository.java`, `settlement/application/OpenDebtPolicy.java`, `settlement/application/SettlementPaymentService.java`, command records `RecordSettlementPaymentCommand`, `WaiveDebtCommand` in `settlement/application`
- Test: `settlement/application/SettlementPaymentServiceTest.java` (Mockito), `settlement/application/SettlementPaymentServiceDbTest.java` (`@ModuleIntegrationTest`, calls the bean), `settlement/application/OpenDebtPolicyTest.java`

**Interfaces:**
- Consumes: `GroupLedgerRepository#createIfAbsent`, `#findForUpdate`, `#totals`, `#isBooked`; `SettlementPlan.of`; `GroupMembership#requireMember`, `#status`, `#isMember`; `Clock`; `ApplicationEventPublisher`.
- Produces:
  - Migration `settlement_payments(id uuid pk, group_id uuid not null, from_debtor uuid not null, to_creditor uuid not null, amount numeric(19,2) not null check (amount > 0), currency varchar(3) not null, status varchar(20) not null, note varchar(500), created_at timestamptz not null, resolved_at timestamptz, version bigint not null, check (from_debtor <> to_creditor))`; indexes `(group_id, status)` and `(to_creditor, status)`.
  - `SettlementPaymentRepository extends JpaRepository<SettlementPayment, UUID>` with `List<SettlementPayment> findByGroupIdOrderByCreatedAtDesc(UUID)`, `List<SettlementPayment> findByToCreditorAndStatus(UUID, PaymentStatus)`, and `Money reserved(UUID groupId, UUID fromDebtor /*nullable*/, UUID toCreditor /*nullable*/)`: the sum of amounts of this group's payments that are PENDING, or CONFIRMED or WAIVED with no ledger entry whose `reference_id` is the payment id yet (D5), filtered by the non-null party.
  - `OpenDebtPolicy#openDebt(GroupId group, UserId debtor, UserId creditor): Money` = `max(0, min(edge(debtor, creditor) - reserved(debtor, creditor), -balance(debtor) - reserved(debtor, *), balance(creditor) - reserved(*, creditor)))` using `totals` and `SettlementPlan.of` (D3).
  - `SettlementPaymentService`: `SettlementPayment record(RecordSettlementPaymentCommand)`, `SettlementPayment waive(WaiveDebtCommand)`, `SettlementPayment confirm(UserId caller, SettlementPaymentId)`, `SettlementPayment reject(UserId caller, SettlementPaymentId, String note)`, `List<SettlementPayment> history(GroupId, UserId caller)`, `List<SettlementPayment> awaitingMyAnswer(UserId caller)`. `record` and `waive`: `requireMember`, group not ARCHIVED (`GROUP_NOT_OPEN`), the other party is a member, `createIfAbsent` + `findForUpdate` first (D6), then `openDebt`, root method, save, publish. `confirm`/`reject`: load by id (unknown or caller not in that group → `SETTLEMENT_PAYMENT_NOT_FOUND`), group not ARCHIVED, root method, save, publish. `awaitingMyAnswer` keeps only payments whose group is OPEN or CLOSED_FOR_SETTLEMENT and in which the caller is still a member.

- [ ] **Step 1: Write the failing tests.**
  - Mockito, `SettlementPaymentServiceTest`: `recordTakesTheLedgerLockBeforeComputingOpenDebt`, `recordSavesAndPublishesTheRecordedEvent`, `aRefusedRecordSavesAndPublishesNothing`, `archivedGroupRefusesEveryOperation`, `confirmOfAnUnknownPaymentIsNotFoundAndPublishesNothing`.
  - Mockito, `OpenDebtPolicyTest` (Mallorca balances): `fullDebtToTheOnlyCreditorIsOpen`, `pendingPaymentsReserveTheDebtOfThatPair` (50 € debt, 30 € PENDING → 20 €, a second 30 € is above), `payingBTheOtherCreditorIsStillPossible`, `rejectedPaymentFreesTheReservation`, `aVanishedPlanEdgeClampsToZero`, `creditorCreditCapsThePayment`, `aConfirmedPaymentNotYetBookedStillReserves`.
  - DB test: `twoConcurrentRecordsOfTheSameDebtorCannotTogetherExceedTheDebt` (two threads, one refused), `reservedSumCountsConfirmedUntilTheLedgerEntryExists`, `confirmAndRejectAtOnceLeaveExactlyOneAnswer` (optimistic lock → one `PAYMENT_NOT_PENDING` or conflict mapped to it), `anApiPortCallOutsideATransactionDoesNotFailLazily` (pitfall 1 of document 02).
- [ ] **Step 2:** `./mvnw -q test -Dtest='SettlementPaymentServiceTest,OpenDebtPolicyTest'` → FAIL.
- [ ] **Step 3:** Write the migration and implement the types above. Map `OptimisticLockingFailureException` on confirm/reject to `PAYMENT_NOT_PENDING` (409). Seed ledger totals in the DB test through `GroupLedgerService.bookExpense` with `ExpenseRecordedFixture`.
- [ ] **Step 4:** `./mvnw -q test -Dtest='SettlementPaymentServiceTest,OpenDebtPolicyTest,SettlementPaymentServiceDbTest'` → PASS.
- [ ] **Step 5:** Format, commit `feat(settlement): record, confirm, reject and waive settlement payments with reserved open debt (R16, R17)`.

### Task 5: Ledger books confirmed payments and waivers

**Files:**
- Modify: `settlement/domain/LedgerEntryType.java` (add `PAYMENT_SENT`, `PAYMENT_RECEIVED`, `DEBT_WAIVED`), `settlement/domain/GroupLedger.java`, `settlement/application/GroupLedgerService.java`
- Create: `settlement/application/SettlementPaymentConfirmedListener.java`, `settlement/application/DebtWaivedListener.java`
- Test: extend `GroupLedgerTest`, `GroupLedgerServiceTest`; create `SettlementPaymentConfirmedListenerTest`, `DebtWaivedListenerTest`, `settlement/SettlementBookingTest.java` (`@ModuleIntegrationTest`)

**Interfaces:**
- Produces: `GroupLedger#appendSettlementPayment(SettlementPaymentConfirmed event, Instant now)` (debtor `+amount` as `PAYMENT_SENT`, creditor `-amount` as `PAYMENT_RECEIVED`, `referenceId` = payment id); `GroupLedger#appendDebtWaiver(DebtWaived event, Instant now)` (debtor `+amount`, creditor `-amount`, both `DEBT_WAIVED`); `GroupLedgerService#bookSettlementPayment(SettlementPaymentConfirmed)` and `#bookDebtWaiver(DebtWaived)`, both idempotent via `isBooked(group, paymentId)` after the lock.

- [ ] **Step 1: Write the failing tests:** domain: `aConfirmedPaymentRaisesTheDebtorAndLowersTheCreditorAndKeepsTheSumAtZero`, `aWaiverBooksDebtWaivedOnBothSides`; service (Mockito): `bookingTheSameConfirmedPaymentTwiceBooksOnce`, `bookingCreatesTheLedgerIfNoExpenseCameFirst`; integration with Awaitility: `confirmingAPaymentMovesBothBalancesAndTheSumStaysZero`, `rejectingAPaymentChangesNoBalance`, `aWaiverMovesBalancesWithoutAConfirmation`, `redeliveringTheConfirmedEventBooksOnce`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='GroupLedgerTest,GroupLedgerServiceTest'` → FAIL.
- [ ] **Step 3:** Implement; listeners mirror `ExpenseRecordedListener`. The unique constraint `settlement_ledger_entry_once` is the last line of defence.
- [ ] **Step 4:** Run the five classes above plus `SettlementBookingTest` → PASS.
- [ ] **Step 5:** Format, commit `feat(settlement): book confirmed settlement payments and waivers in the ledger (R14, R16)`.

### Task 6: Zero-balance events and the group side (cycle broken, D12)

**Files:**
- Create: `shared/events/MemberBalanceStatusChanged.java`, `shared/events/AllBalancesSettled.java`, `group/application/BalanceStatusListener.java`
- Modify: `settlement/application/GroupLedgerService.java` (publish after every booking, expenses included), `group/domain/Group.java` (`updateBalanceStatus`), `group/application/GroupService.java`, `group/domain/Membership.java` (setter via the root only), `ConventionTests.java`, `docs/conventions/01_ARCHITECTURE_AND_NAMING.md`
- Test: `group/domain/GroupTest` (extend), `group/application/BalanceStatusListenerTest`, `settlement/application/GroupLedgerServiceTest` (extend), `group/GroupBalanceStatusTest` (`@ModuleIntegrationTest`), `ConventionTests`

**Interfaces:**
- Produces: `record MemberBalanceStatusChanged(GroupId groupId, UserId userId, boolean hasZeroBalance, Instant occurredAt)` and `record AllBalancesSettled(GroupId groupId, Instant occurredAt)` in `de.hsh.favs.sharewise.shared.events`; `Group#updateBalanceStatus(UserId member, boolean hasZeroBalance): void` (no-op for a non-member, idempotent).
- Rule: after a booking the ledger service compares each affected user's total before and after (the `totals` query); it publishes `MemberBalanceStatusChanged` only when the zero-state flipped, and `AllBalancesSettled` only when everyone is zero afterwards and someone was not before.

- [ ] **Step 1: Write the failing tests:** `GroupTest.updateBalanceStatusSetsTheMemberFlagAndIsIdempotent`, `...ignoresAUserWhoIsNotAMember`; `GroupLedgerServiceTest.anExpenseThatMakesBalancesNonZeroPublishesOneStatusChangePerAffectedMember`, `...aPaymentThatBringsEveryoneToZeroPublishesAllBalancesSettledOnce`, `...nothingIsPublishedWhenNoZeroStateFlips`; integration `confirmingTheLastPaymentSetsHasZeroBalanceOnTheGroupMembers` (Awaitility); `ConventionTests`: events in `shared.events` are exactly these two names.
- [ ] **Step 2:** Run those classes → FAIL.
- [ ] **Step 3:** Implement; the listener calls the group service with the event's fields. Add the exception to document 01 (events live in the publisher's `api`, except these two in `shared.events`: published by settlement, consumed by group, why, change driver) and to `ConventionTests` (allowed names only).
- [ ] **Step 4:** `./mvnw -q test -Dtest='GroupTest,BalanceStatusListenerTest,GroupLedgerServiceTest,GroupBalanceStatusTest,ConventionTests,ModularityTests'` → PASS (`ModularityTests` proves there is no cycle).
- [ ] **Step 5:** Format, commit `feat(group): follow the ledger with hasZeroBalance through shared.events (D12)`.

### Task 7: HTTP endpoints and API contract tests

**Files:**
- Create: `settlement/web/SettlementPlanController.java`, `SettlementPaymentController.java`, `DebtWaiverController.java`, `MySettlementPaymentsController.java`, request/response records (`RecordSettlementPaymentRequest`, `RejectSettlementPaymentRequest`, `WaiveDebtRequest`, `SettlementPaymentResponse`, `SettlementPlanEntryResponse`)
- Test: `settlement/web/SettlementRequests.java` (HTTP set-up), `SettlementPlanControllerTest`, `SettlementPaymentControllerTest`, `DebtWaiverControllerTest`, `MySettlementPaymentsControllerTest`

**Interfaces:**
- Consumes: `SettlementPaymentService`, `OpenDebtPolicy`/`SettlementPlan` (plan with `pendingAmount` from `reserved`), `UserProfiles` (display names, fallback "Unknown user" as in `BalanceController`), `CurrentUser`, `GroupMembership#requireMember`.
- Produces: the endpoints and JSON of Task 1 with field names exactly as the contract.

- [ ] **Step 1: Write the failing tests** (real sign-up via `AccountFixture.signUp`, shared database, unique names, Awaitility for booked balances): `planForMallorcaListsThreeEdgesToAlice`, `recordReturns201PendingAndTheCreditorSeesItInTheInbox`, `recordAboveTheEdgeIs422PaymentExceedsOpenDebt`, `aSecondRecordForTheSameEdgeIsRefusedWhileTheFirstIsPending`, `confirmByTheCreditorIs200AndBalancesFollow`, `confirmByTheDebtorIs403NotTheCreditor`, `confirmByANonMemberIs404SettlementPaymentNotFound`, `confirmWithAPaymentIdOfAnotherGroupIs404`, `confirmTwiceIs409PaymentNotPending`, `rejectWithoutANoteIs400`, `waiveByTheCreditorIs201WaivedAndBalancesFollow`, `waiveByAMemberWhoIsNotOwedIs422`, `amountWithThreeDecimalsOrExponentOrStringIs400`, `nonMemberRecordIs403`, `unknownGroupIs404`, `noTokenIs401`, `anArchivedGroupRefusesRecord` (set the group status through a `GroupMembership` test stub), `everyRefusedCallLeavesNoRowNoEventNoLedgerEntry`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='SettlementPlanControllerTest,SettlementPaymentControllerTest,DebtWaiverControllerTest,MySettlementPaymentsControllerTest'` → FAIL.
- [ ] **Step 3:** Implement the controllers as thin mappers; parse amounts with `Money.eur(String)` so decimals and exponents give `BAD_REQUEST`.
- [ ] **Step 4:** Same command → PASS; then `./mvnw -q verify` → green.
- [ ] **Step 5:** Format, commit `feat(settlement): settlement plan, settlement payment and debt waiver endpoints`.

### Task 8: Frontend, settle-up tab with plan and "I paid"

**Files:**
- Create: `frontend/src/app/api/groups/[groupId]/settlement-plan/route.ts`, `.../settlement-payments/route.ts` (GET, POST), `.../debt-waivers/route.ts`, `frontend/src/app/api/settlement-payments/[paymentId]/confirm/route.ts`, `.../reject/route.ts`, `frontend/src/app/api/me/settlement-payments/route.ts` (all proxies like `balances/route.ts`), `frontend/src/components/SettleUpTab.tsx`, `SettleUpTab.test.tsx`
- Modify: `frontend/src/components/GroupDetail.tsx` (new "Settle up" tab beside Balances)

**Interfaces:**
- Consumes: generated types `components["schemas"]["SettlementPlanEntry"]`, `SettlementPayment`; `useApi`, `ErrorBanner`, `formatEuro`, `amountToCents`; the re-poll pattern of `BalancesTab` (after a record, look again at 500, 1000, 2000, 4000 ms).
- Produces: `SettleUpTab({ groupId, currentUserId })` showing the plan with "I owe" rows (button "I paid", amount prefilled and editable down, disabled while sending) and "Owed to me" rows, the `pendingAmount` as "30,00 € awaiting confirmation" on its row, and a sentence when the plan is empty.

- [ ] **Step 1: Write the failing tests** (Testing Library, mocked fetch): `showsTheRowsWhereIOweAndWhereIAmOwed`, `iPaidSendsTheAmountAndDisablesTheButtonWhileSending`, `showsPendingAmountOnTheRow`, `showsTheProblemMessageWhenTheAmountIsTooHigh`, `emptyPlanSaysEverybodyIsSettled`.
- [ ] **Step 2:** `cd frontend && npm test -- SettleUpTab` → FAIL.
- [ ] **Step 3:** Implement the routes (copy the proxy shape of `balances/route.ts`, POST routes as the expenses route does) and the component.
- [ ] **Step 4:** `npm run lint && npm run typecheck && npm test && npm run build` → PASS.
- [ ] **Step 5:** Commit `feat(frontend): settle-up tab with the plan and "I paid"`.

### Task 9: Frontend, inbox, badge, confirm and reject

**Files:**
- Create: `frontend/src/components/SettlementInbox.tsx`, `SettlementInbox.test.tsx`
- Modify: `GroupDetail.tsx` and the group list page header (badge = count of PENDING settlement payments for me, polled every 30 s and after any answer)

**Interfaces:**
- Consumes: `GET /api/me/settlement-payments?status=PENDING&role=creditor`, confirm/reject routes from Task 8.
- Produces: `SettlementInbox()` rows "Alice says she paid you 30,00 €" with Confirm and Reject (reject asks for a note); `PendingBadge()` showing the count, hidden at zero.

- [ ] **Step 1: Write the failing tests:** `listsPendingSettlementPaymentsAddressedToMe`, `confirmCallsTheRouteAndRemovesTheRow`, `rejectNeedsANoteBeforeSending`, `badgeShowsTheCountAndHidesAtZero`, `alreadyAnsweredShowsTheProblemMessage`.
- [ ] **Step 2:** `npm test -- SettlementInbox` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Full frontend gate (as Task 8, Step 4) → PASS.
- [ ] **Step 5:** Commit `feat(frontend): inbox and badge for settlement payments awaiting an answer`.

### Task 10: Frontend, waive

**Files:**
- Create: `frontend/src/components/WaiveDebtForm.tsx`, `WaiveDebtForm.test.tsx`
- Modify: `SettleUpTab.tsx` ("Waive" button on "Owed to me" rows, and a "Waive manually" form below the plan)

**Interfaces:**
- Produces: `WaiveDebtForm({ groupId, debtors, prefill? })` where `debtors` are the plan entries toward me (dropdown), amount defaults to the edge minus pending, a required note; posts to `/api/groups/{groupId}/debt-waivers`.

- [ ] **Step 1: Write the failing tests:** `waiveButtonOnARowPrefillsDebtorAndAmount`, `dropdownListsOnlyPeopleWhoOweMe`, `noteIsRequired`, `successRefreshesThePlan`, `tooHighAmountShowsTheProblemMessage`.
- [ ] **Step 2:** `npm test -- WaiveDebtForm` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Full frontend gate → PASS.
- [ ] **Step 5:** Commit `feat(frontend): waive a debt from the plan or manually`.

### Task 11: Slice test, end to end, docs, attack

**Files:**
- Modify: `backend/src/test/.../MallorcaSliceTest.java`, `frontend/e2e/mallorca.spec.ts`, `RUNNING.md`, `docs/diagram-deltas.md`, `docs/domain/ShareWise_Domain_Model.drawio` (waive as factory, new fields), `docs/conventions/` where behaviour changed, `docs/devsetup/CATALOG.md`

- [ ] **Step 1:** Extend `MallorcaSliceTest`: after the expenses, Carol records 270.00 to Alice, Alice confirms, balances become Alice +360.00, Bob -90.00, David -270.00, Carol 0.00 with sum 0.00 (verify these by arithmetic: 630-270=360, -270+270=0); a rejected payment changes nothing; a waiver by Alice for Bob's 90.00 brings Bob to 0.00 and Alice to +270.00.
- [ ] **Step 2:** Add the settle-up journey to `mallorca.spec.ts` (Playwright: record, confirm in the inbox, plan shrinks). `npm run e2e` with backend and Postgres running → PASS.
- [ ] **Step 3:** Update `RUNNING.md`, `docs/diagram-deltas.md` (remove what is built; add the deliberate `waive` change and the `shared.events` exception), the diagram, and grep the old terms (`Ex5`, "arrive in a later exercise" in `LedgerEntryType` Javadoc, `Membership.hasZeroBalance` comment).
- [ ] **Step 4:** Run `/edge-case-probe` attack mode; fix the top 1 to 3 test-first, park the rest in the design doc; delete the probe folders; add the CATALOG row.
- [ ] **Step 5:** Run `/review-branch`; triage; `cd backend && ./mvnw verify`, the frontend gate and the redocly lint all green; then merge with `--no-ff` only when asked.

---

## Self-review notes
- Spec coverage: D1 (Tasks 3, 4), D2 (2), D3 to D6 (4), D7 (3, 4), D8 to D9 (3, 4, 7, 10), D10 (4, 7, 9), D12 (6), D13 async booking and D5 (4, 5), D14 lifecycle excluded, package 2 excluded (own plan), failure modes F1 (6), F2 (4), F5 (4), F9 (7), checklist items in the Review Focus tests.
- Found while writing: R15 needs the exact minimum, not greedy (design doc section 5 corrected, Task 2).
- Not in this plan, on purpose: auto-archive (L5), reconciliation scheduler, expiry of PENDING payments, package 2.
