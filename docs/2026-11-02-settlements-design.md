# Settlements: design

> **Superseded in parts (04.11.26):** the settle-up inbox and badge (D10), the filters of `GET /me/settlement-payments` and the idea that confirm re-checks the open debt were replaced. See `2026-11-04-settlements-inbox-design.md` for the decisions and `2026-11-04-money-flow.md` for how the money works today. This document stays as the record of the first design.

Status: draft for approval. Branch `feature/settlements`. Date 2026-11-02.

## 1. Intent
Today ShareWise records expenses and shows each member's balance. Nobody can yet *settle*: see who should pay whom, say "I paid", have the creditor confirm it, or forgive a debt. This feature closes that loop (journey J8, rules R14 to R17, L2). It also makes balances correct when an expense is corrected or removed (R14 counts active expenses only).

Concepts practised: aggregate with a status flow, cross-aggregate invariant under eventual consistency, projection (plan) versus stored state (ledger), outbox events between modules, idempotent listeners.

## 2. What exists (see the survey in the plan)
- `GroupLedger` books `ExpenseRecorded` (async, idempotent, outbox); `GET /groups/{id}/balances`; `BalancesTab`.
- `GroupMembership.status(GroupId)` returns OPEN, CLOSED_FOR_SETTLEMENT, ARCHIVED. No operation changes it yet (lifecycle is a later feature).
- `Membership.hasZeroBalance` exists, always `true`.
- `ExpenseStatus` has CORRECTED and REMOVED, but `Expense` has **no** correct or remove operation, no endpoint, no event.
- The diagram already defines `SettlementPayment`, `PaymentStatus`, the four payment events, `projectSimplifiedPlan`, `DirectedDebt`, ledger entry types `PAYMENT_SENT`, `PAYMENT_RECEIVED`, `DEBT_WAIVED`, `EXPENSE_REVERSAL`.

## 3. Goals, non-goals, anti-goals
**Goals**
- A live settlement plan with the minimum number of payments (R15), visible to debtors and creditors.
- Debtor records a settlement payment (PENDING); creditor confirms or rejects; only CONFIRMED changes balances (R16). Partial payments allowed; never above the open debt (R17).
- Creditor can waive (forgive) all or part of what a debtor owes them, with a note.
- Creditor sees what awaits their answer (in-app inbox with a badge).
- Correcting and removing an expense (item 7) updates balances through ledger reversals.
- Group's `hasZeroBalance` follows the ledger.

**Non-goals (this feature)**
- Group lifecycle: close, reopen, archive (own feature next). Payment code already accepts OPEN and CLOSED_FOR_SETTLEMENT (J8, L2) and refuses ARCHIVED.
- **Auto-archive at zero balance (L5) and its trigger: parked for the team discussion.** We publish `AllBalancesSettled`; whether and how the group reacts is decided in the team.
- Email or push notifications, real money transfer, other currencies.

**Anti-goals** (would be a failure even if it "works")
- A balance changes before the creditor confirms (R16).
- Balances do not sum to 0 (R14), or a balance crosses zero because of a payment.
- Two debtors' payments or a retried event book twice.

## 4. Decisions (as made with the user)
| # | Decision | Why / concept |
|---|---|---|
| D1 | Confirmation flow is in: PENDING, CONFIRMED, REJECTED, WAIVED. | R16 is "Should" in the requirements; including it is **our choice**, not the professor's. |
| D2 | **Plan is a live projection**, never stored. `GroupLedger.projectSimplifiedPlan()` computes it from confirmed balances on every read. Deterministic tie-break: equal balances are ordered by user id. | R15: the plan never changes balances. Nothing to keep in sync. Snapshot "final plan" at close (L4) belongs to the lifecycle feature. |
| D3 | **Open debt is checked per pair and per person.** `record` is allowed iff `0 < amount <= min(edge, debtorDebt, creditorCredit)` where `edge = planEdge(D to C) - pending(D to C)`, `debtorDebt = -balance(D) - pendingBy(D)`, `creditorCredit = balance(C) - pendingTo(C)`. | R17. Pending payments *reserve* debt, so three pending 50 € against a 50 € debt are impossible. The pair cap matches the user's expectation (pay A twice 30 of 50 is refused, pay B is fine); the person caps guarantee no balance ever crosses zero even when the plan later changes. |
| D4 | Reject frees the reservation. Confirm leaves the open debt unchanged (pending falls by what the balance rises by). | Keeps the check stable across the status flow. |
| D5 | CONFIRMED **and WAIVED** payments whose ledger entries are not yet booked still count as reserved (use `isBooked(referenceId)`). `pendingBy`, `pendingTo` and the edge reservation all sum PENDING + unbooked CONFIRMED + unbooked WAIVED. | Confirm and waive are separated from booking by an async event; otherwise a double-pay or pay-after-waive window opens. |
| D6 | `record` **and `waive`** are serialized per group with `GroupLedgerRepository.findForUpdate`. The service computes `openDebt` and passes it to `SettlementPayment.record(..., openDebt)`; the aggregate only checks `amount <= openDebt`, `amount > 0`. | Aggregate rule: no calls to other modules or repositories. Check-then-insert race otherwise. |
| D7 | `SettlementPayment` is its own aggregate; the ledger reacts to its events. Payment holds only ids and value objects. | Matches the diagram edges: payment publishes, ledger subscribes. |
| D8 | **Waive = creditor-initiated write-off**, creating a `SettlementPayment` with status WAIVED (no confirmation, no money moved). Amount is capped by the same D3 rule. The ledger books `DEBT_WAIVED` for both sides with `referenceId` = the payment id. | The reference the user asked for: every ledger entry points at a payment record, so the history shows why a balance moved. |
| D9 | Waive is reachable two ways, same endpoint: a **Waive** button on a plan row (prefilled debtor and amount, editable) and a manual form (debtor dropdown limited to people who owe me, amount, note). | One operation, two entry points. |
| D10 | Notification = in-app **inbox + badge**: endpoint lists PENDING payments addressed to me; the badge is the count. Polling, no push, no email. | J8 "creditor is notified" without a new dependency. |
| D12 | **Cycle broken with neutral events (decided 2026-11-02).** `MemberBalanceStatusChanged` and `AllBalancesSettled` live in `shared.events`, are published only by settlement and consumed only by group (`Group.updateBalanceStatus`, `Membership.hasZeroBalance` stay as in the diagram). Documented as the single exception to "events live in the publisher's `api`" in `docs/conventions/01_ARCHITECTURE_AND_NAMING.md`; `ConventionTests` allows only these two names in `shared.events`. | Module graph stays one-way `account <- group <- expense <- settlement`. It is the only event flowing upstream (survey of all diagram events). Alternatives rejected: allow group -> settlement (a cycle, blocks the P3 service split), a `canLeave` port (group would call settlement), settlement keeping its own copy of group data (biggest change). Change driver for P2: more upstream events would signal that the group/settlement split is wrong. |
| D11 | Item 7 (correct, remove expense) is a **second branch** after the payment flow, same design doc. | It needs new `Expense` operations, endpoints and UI, roughly the size of the payment flow. (call) |

### Calls made while writing (overrule any of these)
- (call) **One edge definition** for `record` and `waive`: the current plan edge D to C, clamped to 0 when the plan has changed (an edge that vanished means 0, the call is refused with `PAYMENT_EXCEEDS_OPEN_DEBT`). The waive dropdown "people who owe me" lists plan edges toward me. Off-plan payments are refused (option B). **Changed 09.11.26:** the plan edge is no longer a cap on the server; `record` and `waive` accept up to the smaller of the debtor's debt and the creditor's credit, the screen still follows the plan (see `2026-11-04-money-flow.md` section 6.2).
- (call) **Who can do what in which group state:** `record` and `waive` in OPEN and CLOSED_FOR_SETTLEMENT; `confirm` and `reject` in OPEN and CLOSED_FOR_SETTLEMENT. ARCHIVED refuses all. The lifecycle feature must refuse archiving while PENDING payments exist, so none can get stuck.
- (call) **`MemberBalanceStatusChanged` also fires after expense booking:** `ExpenseRecordedListener` goes through the same ledger code path, so `hasZeroBalance` is correct for expenses as well as payments. Touching that listener is in scope of package 1.
- (call) **Visibility:** every member of a group sees the whole plan and the full payment history (balances are already visible to all members). The plan endpoint returns all edges; "I owe / owed to me" is a client-side filter.
- (call) **Inbox** `GET /me/settlement-payments` spans all groups the caller belongs to and lists PENDING payments addressed to them in OPEN and CLOSED_FOR_SETTLEMENT groups.
- (call) **Reversals vs. pending (package 2):** a correction or removal never fails because of pending payments; balances stay truthful. A reversal can leave a PENDING payment larger than the new open debt, and confirming it would push a balance across zero. Package 2 must decide what `confirm` does then (refuse, or book and accept the sign flip) in its own design. Flagged here, not solved.

## 5. Domain model (names from the diagram)
- `SettlementPayment` (root): `id: SettlementPaymentId`, `groupId`, `fromDebtor`, `toCreditor`, `amount: Money`, `status: PaymentStatus`, `note`, `createdAt`, `resolvedAt`.
  - `record(GroupId, UserId fromDebtor, UserId toCreditor, Money amount, Money openDebt, Instant now): SettlementPaymentRecorded`
  - `confirm(UserId creditor, Instant now): SettlementPaymentConfirmed` (only toCreditor, only PENDING)
  - `reject(UserId creditor, String note, Instant now): SettlementPaymentRejected` (only toCreditor, only PENDING)
  - `waive(...)`: creates a WAIVED record, see D8.
  - **Diagram delta:** the diagram has `waive(UserId creditor, String note)` without debtor and amount. Because waive creates the record, it becomes a factory with debtor, amount and `openDebt`. The diagram is updated in the same commit (list in `docs/diagram-deltas.md`).
- `GroupLedger` (package 1): add `appendSettlementPayment`, `appendDebtWaiver`, `projectSimplifiedPlan`. New `LedgerEntryType` values `PAYMENT_SENT`, `PAYMENT_RECEIVED`, `DEBT_WAIVED`. `appendCorrectionReversal`, `appendRemovalReversal` and `EXPENSE_REVERSAL` belong to package 2 and are **not** built, stubbed or migrated in package 1.
- `DirectedDebt(fromDebtor, toCreditor, amount)` in `settlement.domain`; `SettlementPlan` read model; `SettlementPaymentId` in `settlement.api` (used in events).
- Events (all in `settlement.api`, end with `Instant occurredAt`): `SettlementPaymentRecorded`, `SettlementPaymentConfirmed`, `SettlementPaymentRejected`, `DebtWaived`, `MemberBalanceStatusChanged`, `AllBalancesSettled`.
- Group side: `Group.updateBalanceStatus(UserId, boolean hasZeroBalance)` driven by `MemberBalanceStatusChanged`. No archive reaction (parked).

### Plan algorithm (R15)
R15 asks for the **minimum** number of payments. Plain greedy matching only guarantees at most n-1, not the minimum (finding the minimum is the problem of splitting the people into the most groups whose balances sum to zero; a group of m people then needs m-1 payments). Our groups are small, so the plan is exact: for up to 16 people with a non-zero balance, a bitmask DP finds the largest number of zero-sum subgroups, and each subgroup is settled by greedy matching inside it (largest debtor pays largest creditor, ties by user id). Above 16 people it falls back to plain greedy and says so in the Javadoc (R15 then holds as "at most n-1"). (call, found while writing the plan.) Sum of edges equals sum of positive balances; payments = n - number of subgroups. Test: sum zero, determinism, the Mallorca numbers (verified with a script before they go into docs), plan never changes balances.

## 6. Flow
1. Debtor opens Settle-up, sees the plan (edges filtered to "I owe" / "owed to me").
2. Debtor taps "I paid" on an edge (amount prefilled, editable down) to `record`; PENDING. Open-debt check D3.
3. Creditor sees a badge and the inbox row "Alice says she paid you 30 €": **Confirm** or **Reject** (with note).
4. Confirm: the aggregate emits `SettlementPaymentConfirmed`; the listener in the ledger books `PAYMENT_SENT` (debtor, +amount) and `PAYMENT_RECEIVED` (creditor, -amount) idempotently; the ledger emits `MemberBalanceStatusChanged` per member whose zero-state flips, and `AllBalancesSettled` when all are zero.
5. Waive: creditor taps Waive on a plan row (or uses the manual form); WAIVED record; `DebtWaived`; ledger books `DEBT_WAIVED` for both.
6. Balances and plan arrive eventually; the UI re-polls as `BalancesTab` does.

Example (50 € debt, 30 € paid): plan says 50 €; record 30 € (PENDING): plan still 50 €, open debt on that edge 20 €, second 30 € refused; confirm: balances -20 € / +20 €, plan says 20 €; reject instead: nothing booked, 50 € again.

## 7. Contract sketch (finalised in `/contract-change`, own commit first)
- `GET /groups/{groupId}/settlement-plan`
- `POST /groups/{groupId}/settlement-payments` (record)
- `GET /groups/{groupId}/settlement-payments` (history)
- `POST /settlement-payments/{id}/confirm`, `/reject`
- `POST /groups/{groupId}/debt-waivers`
- `GET /me/settlement-payments?status=PENDING&role=creditor` (inbox and badge count)
- New error codes in all three places: `PAYMENT_EXCEEDS_OPEN_DEBT`, `SETTLEMENT_PAYMENT_NOT_FOUND`, `PAYMENT_NOT_PENDING`, `NOT_THE_CREDITOR`; reuse `GROUP_NOT_OPEN` (extended to cover ARCHIVED), `NOT_A_MEMBER`, `AMOUNT_NOT_POSITIVE`.

## 8. Failure modes (edge-case-probe, plan mode, 2026-11-02)
Marks: **design** = handled by the design, **test** = goes into the plan as a test from the start, **parked** = knowingly left, **DECIDE** = needs the user.

### Ranked by impact x likelihood
| # | Risk | What happens | What stops it | Mark |
|---|---|---|---|---|
| F1 | **Module cycle.** `settlement` depends on `group::api` (status, membership) and `Group` must subscribe to `MemberBalanceStatusChanged` (diagram), so `group` would depend on `settlement::api`. | `ModularityTests` fails: Spring Modulith forbids cycles; a listener's event type counts as a dependency. The same cycle appears if `leave` (R11) asks settlement for the balance. | Decided in D12: the two balance events live in `shared.events`. Test: `ModularityTests` stays green. | design + test |
| F2 | Confirm and reject (or two confirms) hit the same payment at once. | Both read PENDING, both write; one event too many, or CONFIRMED overwritten by REJECTED. | `@Version` (optimistic lock) on `settlement_payments` plus a `status` check in the aggregate; the loser gets `PAYMENT_NOT_PENDING`. Test with two threads. | design + test |
| F3 | Double click on "I paid" records two PENDING payments of 20 against a 50 debt. | Both pass the open-debt check; the creditor sees two. | Client disables the button while the request runs; the reservation still caps the total. No idempotency key (parked). | parked |
| F4 | Listener never books a CONFIRMED / WAIVED payment (no retry except on restart, see `2026-10-27-event-delivery-and-messaging.md`). | Payment stays reserved forever; the balance stays wrong. | Known limit of the outbox without a retry job. Reservation is conservative, so no over-payment. Mention in the demo; the retry job is the later messaging step. | parked |
| F5 | Plan reshuffles while a payment is PENDING (new expense, other confirmation). | `edge - pending` goes negative for that pair. | Clamp to 0 (call in section 4); person caps still protect other creditors. Test: reshuffle between record calls. | test |
| F6 | Reversal (package 2) makes a PENDING payment larger than the new open debt. | Confirm would cross zero. | Flagged for package 2. | parked |
| F7 | Pending payment survives with all balances 0 (after a removal in package 2); the member leaves (R11 checks balance only). | Payment points at a former member. | Package 2 decides; lifecycle must also refuse leaving with PENDING. | parked |
| F8 | Creditor wants to waive the whole debt while the debtor has a PENDING payment. | Waive is capped to `edge - pending`. | Creditor rejects first, then waives. UI explains it. | design |
| F9 | Rejected call must leave nothing behind. | A refused `record` / `waive` / `confirm` could leave a row or an event. | One test per refusal: no row, no event, no ledger entry. | test |

### Walk of the checklist
- **Business rules at the edge (R14 to R17):** amount = open debt exactly (allowed), open debt + 0.01 (`PAYMENT_EXCEEDS_OPEN_DEBT`), 0 and negative (`AMOUNT_NOT_POSITIVE`), amount with more than 2 decimals (refuse at the API; **test**), currency different from the group's (refuse; **test**). Balances sum to 0 after each of record, confirm, reject, waive: property test on the ledger (**test**).
- **Lifecycle:** every operation x OPEN / CLOSED_FOR_SETTLEMENT / ARCHIVED in a table-driven `@ParameterizedTest` (**test**); every `PaymentStatus` x confirm / reject (only PENDING moves; **test**); WAIVED and CONFIRMED are terminal.
- **Authorization:** `confirm` / `reject` by the debtor or a non-creditor member: 403 `NOT_THE_CREDITOR`; by a non-member of the payment's group: 404 `SETTLEMENT_PAYMENT_NOT_FOUND` (no existence leak); `record` with a creditor id that is not in the path's group: `NOT_A_MEMBER`; `record` to oneself: no plan edge, refused; former members: no access. All **test** at API level.
- **Events:** the same `Confirmed` twice: second booking is a no-op (unique constraint, `isBooked`; **test**); both ledger entries are written in one transaction, so a failure halfway leaves none; `MemberBalanceStatusChanged` is emitted only when the zero-state flips and `Group.updateBalanceStatus` is idempotent (**test**); events for different payments commute (sums), so order does not matter.
- **Concurrency:** two `record` calls of one debtor: serialized by D6 (**test** with threads, expects the second refused when together they exceed the debt); `record` vs. booking listener: both lock the ledger row.
- **Input shape:** reject note blank or longer than 500 characters, unicode, amount as string or `1e3`: documented `400 BAD_REQUEST` (**test**).
- **Docs vs code:** `GROUP_NOT_OPEN` is extended to ARCHIVED; check `frontend/src/lib/errors.ts` texts and the contract description. The diagram delta for `waive` is listed in `docs/diagram-deltas.md`. R16 says confirmation is optional in the MVP; our choice to include it is stated in D1.
- **Builder note:** `MemberBalanceStatusChanged` needs "balance before / after" in the ledger append. Use the existing `totals` query, do not load all entries into the aggregate.

## 8b. Review findings (design-review, independent Opus read, 2026-11-02)
- **Kept as designed:** both person caps, the pair cap (user chose B over per-person caps only; **dropped 09.11.26**, see money-flow section 6.2), the lock on `record` and `waive`, `@Version` on the payment, repeat-safe booking, the live plan, the tie-break, cross-group inbox and the manual waive form (user's choices). `AllBalancesSettled` is still published, consumer parked with L5.
- **Fixed in the doc:** waive takes the same ledger lock as `record` (D6).
- **Diagram delta, deliberate:** the diagram draws `waive(creditor, note)` as an instance method next to `confirm` / `reject`. Making it a factory that creates a WAIVED record **changes its meaning**, not only its signature. Raise it with the team; until then D8 stands as our reading.
- **`WAIVED` is a different kind of record** (creditor-started, created finished, no money moved). Every query for "money that moved" must exclude it.
- **Package 2 warning:** `isBooked(group, referenceId)` matches on `referenceId` alone and the unique key is `(reference_id, member_id, entry_type)`. A reversal referenced by `expenseId` looks already booked and a second correction collides on `EXPENSE_REVERSAL`. Package 2 needs a revision or event id as reference; do not copy D5's use of `isBooked` blindly.
- **R11 under eventual consistency (inherent):** an expense recorded but not yet booked is invisible to the balance flag, so a leave can slip through in that window. Stated limit.
- **Inbox cost:** `GroupMembership.status()` loads a group per call and no "groups of this user" port exists; the inbox needs a new port method or accepts N calls.

## 11. Still open
- Nothing blocking. **Decided 2026-11-02 (user): booking of confirm / waive stays async via the event** (D5 and F4 stay). The review's alternative, booking in the same transaction under the ledger lock, was considered and not taken: it would remove D5, the join query and stuck payments but give up one-aggregate-per-transaction.
- **Later stage (not in this feature):** a scheduled **reconciliation job** that closes the F4 gap. Two candidates: (a) re-publish incomplete events from the event publication registry on a schedule instead of only on restart; (b) sweep CONFIRMED / WAIVED payments that have no ledger entries yet (`isBooked` false after N minutes) and book them through the same idempotent code path. Both are safe because booking is idempotent. Belongs to the messaging/outbox step; note it as a change driver for P2/P3.

## 9. Work packages (branches, merged with `--no-ff`)
1. `feature/settlements`: contract, plan, `SettlementPayment`, ledger booking, `updateBalanceStatus`, frontend (plan, I paid, inbox, waive), slice test, docs.
2. `feature/expense-correct-remove`: `Expense.correct` / `Expense.remove`, events `ExpenseCorrected`, `ExpenseRemoved`, endpoints and UI, ledger reversals.
Then, separately: group lifecycle; team discussion of auto-archive.

## 10. Left to the builder
Exact table columns of `settlement_payments` (migration V403), JSON field names (`openapi.yaml` decides), listener class names, query shapes for the reservation sums.

## 8c. Attack findings (independent Opus attacker, 2026-11-03)
The attacker reproduced eight issues; none was found by our own tests. Fixed: A4 (a NUL character in a note gave 500; now 400, `NoteRule`), A5 (amounts outside the contract pattern were accepted; now 400, `AmountText`), A8 (wrong rule range in a `SettleUpTab` comment). Held up: concurrent confirms, record against waive races, a repeated confirm event, all authorization checks, the amount and note edge cases.

| Id | Finding | Status |
|---|---|---|
| A1 | A PENDING payment can be confirmed after a later plain expense has already zeroed the pair; booking it makes the creditor negative (50.00 payment, Bob then pays a 50.00 expense for Alice, Alice confirms: Alice -50.00). `OpenDebtPolicy` only caps at `record` time. F6 placed this risk in package 2, but package 1 already reaches it. | **DECIDE** (user): confirm refuses or caps, or the anti-goal is reworded |
| A2 | `AllBalancesSettled` is published while a PENDING payment is open. The parked auto-archive (L5) would archive a group with an open payment. | **DECIDE** (user), before L5 consumes the event |
| A3 | After a plan reshuffle the plan can show an edge the server always refuses with `PAYMENT_EXCEEDS_OPEN_DEBT` (the person cap is used up on another pair, `pendingAmount` only counts the pair). | **DECIDE** (user): plan counts the person-level reservation |
| A6 | Note length: contract counts characters, Bean Validation counts UTF-16 units (300 emoji are refused). | parked, low |
| A7 | `confirm` / `reject` answer 422 `GROUP_NOT_OPEN` for an archived group; the contract lists no 422 there. | parked, needs `/contract-change` |
| A9 | The contract's Amount pattern accepts `010.00`; the code now refuses leading zeros (stricter than the contract). Tighten the pattern in a contract commit. | parked, low |
Not probed: former members (no leave operation yet), out-of-order `MemberBalanceStatusChanged`, HTTP-level concurrency, more than 16 non-zero members (greedy fallback), a listener failing halfway (F4). The UI limits notes to 200 characters while the API allows 500; `PaymentForm` lets a 0 amount through to the server.
