# Review: settlements

Branch `feature/settlements` (52 commits on top of `main`). Before: the app recorded expenses and showed balances, but nobody could settle a debt. Now: a debtor reports a payment, the creditor confirms or rejects it, the creditor can waive a debt, and the group sees the fewest transfers that settle everybody; waiting payments and their outcomes arrive in the one bell. New: `SettlementPayment` aggregate, the plan, the reservation of open debt, `shared.events`, three notification types.

How the money works (diagrams, verified numbers): `docs/2026-11-04-money-flow.md`. Read it first.

## Features and where to start

Backend paths are under `backend/src/main/java/de/hsh/favs/sharewise/`, frontend under `frontend/src/`. Follow **controller → service → domain (the rules) → repository**, then the test.

| Feature | Start at (backend) | Frontend | Test to read |
|---|---|---|---|
| **Report a payment** | `settlement/web/SettlementPaymentController.recordSettlementPayment` `POST /groups/{id}/settlement-payments` → `SettlementPaymentService.record` → `OpenDebtPolicy.openDebt` → `SettlementPayment.record` | `components/SettleUpTab.tsx` ("I paid") | `SettlementPaymentTest`, `SettlementPaymentServiceDbTest` |
| **Confirm or reject** | `SettlementPaymentController.confirmSettlementPayment` / `rejectSettlementPayment` → `SettlementPaymentService` → `SettlementPayment.confirm/reject` | `components/InboxBell.tsx` (payment row) | `SettlementPaymentControllerTest` |
| **Waive a debt** | `SettlementPaymentController.waiveDebt` `POST /groups/{id}/debt-waivers` → `SettlementPaymentService.waive` → `SettlementPayment.waive` | `components/WaiveDebtForm.tsx` | `DebtWaiverControllerTest` |
| **See the plan** | `settlement/web/SettlementPlanController.getSettlementPlan` → `SettlementPlanService.listPlanEntries` → `SettlementPlan.of`, `OpenDebtPolicy.payableEdges` | `SettleUpTab.tsx` | `SettlementPlanTest`, `OpenDebtPolicyTest` |
| **Payments waiting for me** | `SettlementPaymentController.listMySettlementPayments` `GET /me/settlement-payments` → `SettlementPaymentService.listAwaitingMyAnswer` | `app/api/inbox/route.ts` → `InboxBell.tsx` | `MySettlementPaymentsControllerTest`, `app/api/inbox/route.test.ts` |
| **Booking into the ledger** (event) | `settlement/application/GroupLedgerListener` (`on(ExpenseRecorded)`, `on(SettlementPaymentConfirmed)`, `on(DebtWaived)`) → `GroupLedgerService.book*` → `GroupLedger.append*` | – | `SettlementBookingTest`, `GroupLedgerServiceTest` |
| **Zero-balance flag** (event) | `group/application/BalanceStatusListener` → `Group.updateBalanceStatus` → `Membership` | – | `GroupTest` |
| **"Confirmed / rejected / waived" entries** (event) | `notification/application/SettlementPaymentConfirmedListener`, `SettlementPaymentRejectedListener`, `DebtWaivedListener` → `NotificationService` | `InboxBell.tsx` | `NotificationEventsTest` |
| **The whole journey** | – | – | `MallorcaSliceTest`, `frontend/e2e/mallorca.spec.ts` |

```mermaid
sequenceDiagram
  participant D as Debtor
  participant S as SettlementPaymentService
  participant C as Creditor (bell)
  participant L as GroupLedgerService
  D->>S: record (lock ledger, open debt, save PENDING)
  Note over S: ledger unchanged, amount is reserved
  C->>S: confirm (status CONFIRMED)
  S-->>L: SettlementPaymentConfirmed (event, after commit)
  L->>L: book PAYMENT_SENT / PAYMENT_RECEIVED once
  L-->>D: MemberBalanceStatusChanged to group; entry "confirmed" to the payer
```

## Know these before you read

- **Confirm does not re-check the open debt.** R17 is checked on report and waive; confirm records a fact (the first handover's A1 was withdrawn, `money-flow` section 8).
- **A PENDING payment reserves the open debt** under the ledger lock (D5, D6), so one debt cannot be reported twice; `@Version` only serialises two answers to one payment.
- **The plan is never stored**; `payableAmount` is what the screen offers on a plan edge: the edge minus what is reserved, never more than the open debt `record` accepts (`OpenDebtPolicy`). The server does not cap by the plan edge (money-flow section 6.2).
- **`shared.events` is the one exception** to "events live in the publisher's `api`" (group would otherwise depend on settlement, a cycle); `ConventionTests` pins it.
- **State is a query, history is an event:** payments waiting for me come from `GET /me/settlement-payments`, outcomes are notification entries; `notification` now depends on `settlement::api` and stays a leaf.
- **Exact minimum plan up to 16 people, greedy above** (R15; our choice, not from the slides); `Membership.balanceStatusAt` ignores an older zero-flag event (D12).
- Reasons and the independent design review: `docs/2026-11-04-settlements-inbox-design.md`.

## Diff and run

```sh
D="git diff -w --ignore-cr-at-eol"     # hides line-ending noise
$D main...HEAD -- docs/api/openapi.yaml                                   # the contract: a good first read
$D main...HEAD -- backend/src/main/java/de/hsh/favs/sharewise/settlement  # one area at a time
git show 9e16fee                                                          # the contract commit with its message
cd backend && ./mvnw -q verify      # Docker must run
cd frontend && npm test && npm run e2e
```

## Open

- Not run after the last fixes: the full backend `verify` (the backend agent's run of 383 tests was before them; I re-ran `GroupLedgerServiceTest` and `SettlementPaymentServiceTest`) and `npm run e2e` (needs backend, Postgres, Keycloak). The push gate was stopped by low memory. Frontend `npm test`: 192 passed.
- Parked with a trigger: auto-archive (L5) must ask for pending payments by query; archive and leave checks on answers come with `archiveGroup` / `leaveGroup`; `reject` overwrites the debtor's note.
- The bell shows a waiting payment's group only as an "Open group" link (`SettlementPayment` has no group name; a contract field would fix it).
- Needs the team or professor: diagram deltas S1 to S4 (`docs/diagram-deltas.md`), R15's limit of 16, R16 optional in the MVP (our choice D1).
