# Design: settlements on the new main (one inbox, simpler checks)

**Date:** 04.11.26 · **Status:** v1, decisions taken with the user, waiting for the failure-mode and design review · **Branch:** `feature/settlements` (main merged in, `dd8492a`) · **Builds on:** `2026-11-02-settlements-design.md`, `2026-11-03-settlements-handover.md`, `2026-11-03-notifications-design.md`

Marks: **(decided)** the user decided it, **(call)** our choice taken without asking, can be overruled.

## 1. Intent and why
`main` now has a bell (invitations as a query, what happened as notification entries). The settlements branch was built before it and brought its own inbox (`SettlementInbox`, `PendingBadge`, polling `GET /me/settlement-payments`). Two places for "something waits for me" tell the same lesson twice, differently. This work puts settlements into the one model and removes checks that guard cases the app cannot produce.

Concepts this practises (P2/P3): **state versus event** (a payment waiting for my answer is state, a query; confirmed / rejected / waived are things that happened, events), **bounded context and context map** (notification becomes a downstream consumer of settlement's `api`), **eventual consistency** (an entry shows up moments after the answer), **idempotent consumer** (a redelivered event writes one row), **aggregate rule** (R17 is checked where the state changes, in the aggregate), **reservation** (a PENDING payment holds part of the open debt so it cannot be spent twice).

## 2. Decisions
| # | Decision | Source |
|---|---|---|
| I1 | **One bell.** A payment waiting for the creditor's answer is shown in the bell like an open invitation: asked from `settlement` (a query, `GET /me/settlement-payments`, PENDING and as creditor), not stored as a notification. Confirm and Reject sit on that row. | decided |
| I2 | What happened becomes entries in `notification`: `PAYMENT_CONFIRMED` and `PAYMENT_REJECTED` (to the payer, actor = creditor), `DEBT_WAIVED` (to the debtor, actor = creditor). No entry for "payment recorded": that is the waiting row of I1. | follows I1 and notification design N7/N9 |
| I3 | `notification` depends on `settlement::api` (module line in `package-info.java`, document 01 sections 1 and 3.6). It stays a leaf: nothing depends on it. | follows I1/I2 |
| I4 | Removed from the frontend: `SettlementInbox`, `PendingBadge`, their polling, the Dashboard wrapper. The settle-up tab keeps the plan, "I paid" and "Waive". | follows I1 |
| A1 | **Confirm does not re-check the open debt.** R17 is enforced when a payment is reported and when a debt is waived, under the ledger lock. Confirm records a fact (the money arrived, R16). *Withdrawn: our first proposal (confirm refuses when the debt is gone) was wrong; see the design review below and `2026-11-04-money-flow.md` section 8.* | decided by the design review and our check of it |
| A2 | **Dropped.** `AllBalancesSettled` keeps one meaning, "all balances are zero". Auto-archive (L5) must ask for pending payments by query when it runs. Parked with that trigger. | decided |
| A3 | **Fixed with a new field.** `SettlementPlanEdge.payableAmount` is what the server would accept now, computed by the same `OpenDebtPolicy` as `record`, limited to the plan edge (changed 09.11.26: `record` itself no longer caps by the plan edge, so the screen never offers more than the server accepts); `pendingAmount` keeps its meaning (reserved on this pair). The "I paid" button shows only when `payableAmount` is above 0. | decided (option A) |
| A4 | **Dead checks removed.** No operation sets a group ARCHIVED or a member FORMER_MEMBER yet, so the archive and former-member filters in `awaitingMyAnswer` go (one query, the N+1 with them) and confirm/reject get no `GROUP_NOT_OPEN` in the contract. Parked with the trigger "when `archiveGroup` or `leaveGroup` is built". The `isMember` check in `loadForAnswer` stays (it hides payment ids from outsiders). | decided |

### Design review (independent read-only reviewer, Opus, 04.11.26)
What it found, how we verified it, and what we did. Kept for P2 (limits and change drivers).
- **A1 recorded a falsehood.** With a third person, refusing Alice's confirm makes her reject money she received, and the ledger never records Bob's transfer. Booking it gives the true picture (Alice owes Carol). The justification "no balance crosses zero at both moments" was wrong: the balance moves at booking, and expenses already cross zero unchecked. We re-derived it with a script (money-flow section 8) and withdrew A1. F9 and the self-reservation trap existed only because of A1.
- **A2 put a state into an event** and left a hole (a rejected last payment never publishes). Dropped.
- **Checks for states the app cannot produce** (verified by grep: only the enums mention ARCHIVED and FORMER_MEMBER). Removed (A4).
- **One field, two meanings** (`pendingAmount`). Fixed with `payableAmount` (A3).
- **Left as it is, on the reviewer's advice:** the reservation with the ledger lock, the "confirmed but not yet booked still reserved" clause (D5), `@Version`, the idempotent notification insert, `balanceStatusAt` (the migration exists; removing it costs more), the bitmask DP, the direction `notification → settlement::api`.
- **Change driver for P3:** `MemberBalanceStatusChanged` orders by wall-clock instant; across services with a broker a per-ledger sequence number is more robust.

## 3. How the parts fit
```
settlement  --SettlementPaymentConfirmed/Rejected, DebtWaived (api events)--> notification (listener, one row per event)
settlement  <-- GET /me/settlement-payments (state, a query) -- /api/inbox (Next.js) --> InboxBell
group       <-- GET /invitations (state) ---------------------/        notification <-- GET /notifications (events)
```
The bell's route handler asks three sources and merges: open invitations, payments waiting for my answer, entries. A part that fails is shown as failed, the others still arrive (as today with two).

## 4. Keep it simple: what is dropped, parked or fixed
Applies `docs/conventions/03` section 7 to the handover's lists.

| Item | Verdict | Why |
|---|---|---|
| Booking skeleton three times | already one `book(...)` in `GroupLedgerService`; nothing to do | the handover was written before that extraction |
| Balance mapping from `ledgers.totals` in three places (`OpenDebtPolicy`, `SettlementPlanService`, `BalancesImpl`) | **extract once** (rule of three holds) | one place for one rule |
| N+1 in `awaitingMyAnswer` and its archive and former-member filters | **remove the filters** (A4), one query | no operation produces those states yet |
| `reserved` JPQL reads `LedgerEntry` of another aggregate | **document** as the deliberate exception (D5) | splitting the query costs more than the exception |
| `pendingAmount` vs what the user can do | **new field `payableAmount`** (A3); `pendingAmount` keeps "reserved on this pair" | one field, one meaning |
| `updateBalanceStatus` returns void | **change the rule text** (backend-domain rule, document 01 section 3.1): an operation that reacts to an event and produces none returns nothing | the diagram's operation has no event to return |
| `GET /me/settlement-payments` answers 400 for allowed values | **narrow the contract**: no `role`, no `status`; it answers the caller's payments waiting for their answer | nobody needs the other filters |
| `reject` overwrites the debtor's note | **park** | a lost remark; needs a new column and a contract field |
| `PaymentForm` lets 0 or negative through | **drop** | the server answers `AMOUNT_NOT_POSITIVE` and the banner shows it |
| Out-of-order guard with the wall clock (`balance_status_at`) | **keep, named in the report** | see section 6 |
| A6 emoji length, "greedy above 16 people says nothing", former members, HTTP-level concurrency, listener failing halfway | **park** | cannot happen with the data a student group produces, or no operation exists yet |
| A7 (422 `GROUP_NOT_OPEN` on confirm/reject) | **drop**, parked with the trigger of A4 | no archive operation yet |
| A9 (Amount pattern accepts `010.00`) | **fix in the contract commit** | cheap, the contract must match the code |
| UI nits: `role="status"` on the badge, duplicate fetch, inbox retry, waive note lost on re-click | **gone with I4** or dropped | the components they live in are removed |

## 5. Contract changes (own commit, `/contract-change`)
1. `GET /me/settlement-payments`: remove `role` and `status`; summary and description say "waiting for my answer".
2. `SettlementPlanEdge`: new field `payableAmount` (A3), `pendingAmount` description made exact ("reserved on this pair, including confirmed payments not yet booked"). Fix the example order.
3. `Amount` pattern without leading zeros (A9).
4. `NotificationType` gains `PAYMENT_CONFIRMED`, `PAYMENT_REJECTED`, `DEBT_WAIVED`; the inbox example grows one entry.

No new error code and no new status code on confirm/reject (A1 withdrawn, A4).

## 6. Mechanisms kept on purpose, with their cost (for the user to overrule)
- **Exact minimum plan up to 16 people (bitmask DP, R15).** About 160 lines against a greedy plan of 30. Harm it prevents: one transfer more than needed in a group of friends. Our choice, not from the slides. Kept: it is the core domain algorithm and the strongest part of the defence. Cost if wrong: a bigger class to explain.
- **`Membership.balanceStatusAt` and the "ignore an older event" rule (D12, S3/S4).** Harm it prevents: a late `MemberBalanceStatusChanged` flips the zero flag back and a member with a debt could leave (R11); `leaveGroup` does not exist yet, so today it protects a rule nobody can break. Cost: one column, one migration, a signature that differs from the diagram. Alternative: drop it until `leaveGroup` is built, then ask the ledger synchronously (a port from `group` to `settlement`, which is the cycle `shared.events` avoids). Kept for now: removing it later needs a migration and the diagram delta S3/S4 undone.
- **`@Version` on the payment plus the ledger row lock (D6).** The lock serialises record and waive (the reservation rule needs it); `@Version` serialises two answers to one payment. Each guards a different pair, neither is redundant.

## 7. Failure modes
`/edge-case-probe` plan mode, 04.11.26, proportionate: only what the real app can produce; revised after the design review. Ranked by impact × likelihood.

| # | Who hits it, how often | What happens | Verdict |
|---|---|---|---|
| F1 | A creditor confirms a payment after a later expense changed the pair (the old A1). Rare. | Confirm books the fact; the next plan shows the true picture (money-flow section 8). | **Handled by design (A1 withdrawn).** **Test:** one service test that confirm after a later expense books the payment and nobody is refused. |
| F2 | A reported payment is idle until the creditor answers. Common. | The reservation stays; the debtor cannot report the same debt again. | **Accepted**: no timeout (parked, trigger: users complain). |
| F3 | A payer is told "confirmed/rejected/waived" twice because the event is redelivered. Happens after a crash, rarely. | A second inbox row. | **Handled by design**: `UNIQUE (recipient, type, reference, occurredAt)` with `insertIfAbsent`, the same for all types. **Test:** one listener test that delivers the same event twice and finds one row. |
| F4 | A person opens the bell the moment after the creditor answered. Always, for about a second. | The waiting row is gone at once (query), the payer's entry arrives moments later (eventual consistency). | **Handled by design**; the bell polls. No code. |
| F5 | Confirm and reject of one payment at the same moment (two tabs of one creditor). Rare. | One answer wins. | **Handled by design**: `@Version` makes the second answer fail with a conflict the UI treats as "already done". Existing test. |
| F6 | A payer whose account is inactive or who left the group gets an entry. Rare. | The entry names "Someone" / "a group" when a name cannot be resolved. | **Handled by design** (null-safe lookup already in `NotificationService.list`). |
| F7 | The plan shows an edge the debtor cannot pay (A3). After any reshuffle while a payment is pending. | The button used to fail with 422. | **Fixed by `payableAmount`.** **Test:** the handover case (Bob 50.00 PENDING to Alice, Carol pays 50.00 for Alice, edge Bob to Carol has `payableAmount` 0.00). |
| F8 | The plan computes the payable amount for every edge. Every plan view. | Rebuilding the plan once per edge would be wasteful. | **Design rule:** one method on `OpenDebtPolicy` that returns the payable amount of all edges from one read of the balances; it is also the single place that maps `ledgers.totals`. |
| F9 | `notification` depends on `settlement::api`: a cycle if settlement ever listens to a notification event. Never today. | `ModularityTests` refuses it. | **Handled by the existing architecture test** (`nothingDependsOnTheNotificationModule` stays). |

Parked with a trigger: **L5 must ask for pending payments by query when it archives** (A2 dropped). Not listed: a listener failing halfway (parked in the first settlements design), former members and archived groups (no operation yet, A4), HTTP-level concurrency of the bell (read only).
