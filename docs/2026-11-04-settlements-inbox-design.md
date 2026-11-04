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
| A1 | **Confirm refuses when the open debt is gone** (422 `PAYMENT_EXCEEDS_OPEN_DEBT`, payment stays PENDING, the creditor can reject). R17 is enforced at both moments the balance can move: when the payment is reported and when it is confirmed. | recommended by us, user asked for the case to be explained, default taken (call) |
| A2 | `AllBalancesSettled` is only published when no payment of the group is PENDING. | decided |
| A3 | The plan shows what can really be paid: an edge's `pendingAmount` is the part that cannot be paid now (reserved on the pair or used up by the person's other reservations), computed by the same `OpenDebtPolicy` the server uses on `record`. | decided |

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
| N+1 in `awaitingMyAnswer` | **fix**: ask once per distinct group, not once per payment | a person has few groups; no new port method |
| `reserved` JPQL reads `LedgerEntry` of another aggregate | **document** as the deliberate exception (D5) | splitting the query costs more than the exception |
| `pendingAmount` wording | **fix in the contract**, together with A3 | it counts reserved payments, not only PENDING |
| `updateBalanceStatus` returns void | **change the rule text** (backend-domain rule, document 01 section 3.1): an operation that reacts to an event and produces none returns nothing | the diagram's operation has no event to return |
| `GET /me/settlement-payments` answers 400 for allowed values | **narrow the contract**: no `role`, no `status`; it answers the caller's payments waiting for their answer | nobody needs the other filters |
| `reject` overwrites the debtor's note | **park** | a lost remark; needs a new column and a contract field |
| `PaymentForm` lets 0 or negative through | **drop** | the server answers `AMOUNT_NOT_POSITIVE` and the banner shows it |
| Out-of-order guard with the wall clock (`balance_status_at`) | **keep, named in the report** | see section 6 |
| A6 emoji length, "greedy above 16 people says nothing", former members, HTTP-level concurrency, listener failing halfway | **park** | cannot happen with the data a student group produces, or no operation exists yet |
| A7 (422 `GROUP_NOT_OPEN` on confirm/reject), A9 (Amount pattern accepts `010.00`) | **fix in the same contract commit** | cheap, the contract must match the code |
| UI nits: `role="status"` on the badge, duplicate fetch, inbox retry, waive note lost on re-click | **gone with I4** or dropped | the components they live in are removed |

## 5. Contract changes (own commit, `/contract-change`)
1. `POST /settlement-payments/{id}/confirm`: add 422 `PAYMENT_EXCEEDS_OPEN_DEBT` (A1) and 422 `GROUP_NOT_OPEN` (A7); the same `GROUP_NOT_OPEN` on reject.
2. `GET /me/settlement-payments`: remove `role` and `status`; summary and description say "waiting for my answer".
3. `SettlementPlanEdge.pendingAmount`: new description (A3). Fix the example order.
4. `Amount` pattern without leading zeros (A9).
5. `NotificationType` gains `PAYMENT_CONFIRMED`, `PAYMENT_REJECTED`, `DEBT_WAIVED`; the inbox example grows one entry.

## 6. Mechanisms kept on purpose, with their cost (for the user to overrule)
- **Exact minimum plan up to 16 people (bitmask DP, R15).** About 160 lines against a greedy plan of 30. Harm it prevents: one transfer more than needed in a group of friends. Our choice, not from the slides. Kept: it is the core domain algorithm and the strongest part of the defence. Cost if wrong: a bigger class to explain.
- **`Membership.balanceStatusAt` and the "ignore an older event" rule (D12, S3/S4).** Harm it prevents: a late `MemberBalanceStatusChanged` flips the zero flag back and a member with a debt could leave (R11); `leaveGroup` does not exist yet, so today it protects a rule nobody can break. Cost: one column, one migration, a signature that differs from the diagram. Alternative: drop it until `leaveGroup` is built, then ask the ledger synchronously (a port from `group` to `settlement`, which is the cycle `shared.events` avoids). Kept for now: removing it later needs a migration and the diagram delta S3/S4 undone.
- **`@Version` on the payment plus the ledger row lock (D6).** The lock serialises record and waive (the reservation rule needs it); `@Version` serialises two answers to one payment. Each guards a different pair, neither is redundant.

## 7. Failure modes
`/edge-case-probe` plan mode, 04.11.26, proportionate: only what the real app can produce. Ranked by impact × likelihood.

| # | Who hits it, how often | What happens | Verdict |
|---|---|---|---|
| F1 | A creditor confirms a payment whose debt was cleared by a later expense (A1). Rare, but it is money. | Without a rule: the creditor goes below zero. | **Handled by design (A1)**: the aggregate re-checks the open debt in `confirm`. **Test:** service/domain test that confirm is refused, the payment stays PENDING and nothing is booked. |
| F2 | The creditor of a pending payment answers while the debt is reduced by a *waiver* the same person made. Rare. | Same shape as F1, same rule. | **Handled by F1.** No second test. |
| F3 | A payer is told "confirmed/rejected/waived" twice because the event is redelivered. Happens after a crash, rarely. | A second inbox row. | **Handled by design**: `UNIQUE (recipient, type, reference, occurredAt)` with `insertIfAbsent`, the same for all types. **Test:** one listener test that delivers the same event twice and finds one row. |
| F4 | A person opens the bell the moment after the creditor answered. Always, for about a second. | The waiting row is gone at once (query), the payer's entry arrives moments later (eventual consistency). | **Handled by design**; the bell polls. No code. |
| F5 | Confirm and reject of one payment at the same moment (two tabs of one creditor). Rare. | One answer wins. | **Handled by design**: `@Version` makes the second answer fail with a conflict the UI treats as "already done". Existing test. |
| F6 | Someone confirms in a group that was archived meanwhile. Rare. | `GROUP_NOT_OPEN` 422. | **Handled**; the contract lists it now (A7). |
| F7 | A payer whose account is inactive or who left the group gets an entry. Rare. | The entry names "Someone" / "a group" when a name cannot be resolved. | **Handled by design** (null-safe lookup already in `NotificationService.list`). |
| F8 | The plan page computes the payable amount per edge (A3). Every plan view. | Computing the open debt per edge would rebuild the plan once per edge (up to 16 people, bitmask DP). | **Design rule:** `OpenDebtPolicy` gets one method that returns the payable amount of all edges from one read of the totals. **Test:** the plan test from the handover (Bob 50 pending to Alice, Carol pays 50 for Alice, edge Bob→Carol shows pending 50). |
| F9 | A2 plus A1 together: a payment stays PENDING although everyone is at zero (F1 case); the creditor rejects it later. Rare. | `AllBalancesSettled` is published only at a booking, and a reject books nothing, so it is never published for this group. | **Parked with a trigger (L5).** The event has no consumer yet. When auto-archive is built it must decide how it learns "zero and nothing pending" (publish after a reject too, or check by a query when it runs). Costs nothing now. **Needs the user's nod** (it softens A2 as decided). |
| F10 | `notification` depends on `settlement::api`: a cycle if settlement ever listens to a notification event. Never today. | `ModularityTests` refuses it. | **Handled by the existing architecture test** (`nothingDependsOnTheNotificationModule` stays). |

Not listed: a listener failing halfway (parked in the first settlements design, F4 there), former members (no leave operation yet), HTTP-level concurrency of the bell (read only).
