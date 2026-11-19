# Design review: the lock in `record` and the plan inside the open debt

**Date:** 09.11.26 · **Reviewer:** independent read-only agent (Opus), triaged by the main session · **Status:** findings; the user decides (section 5) · **Scope:** `SettlementPaymentService.record/waive`, `OpenDebtPolicy`, `SettlementPlan`, the `reserved` query, `SettleUpTab`.

## 1. The two questions
1. *Is "Carol reports twice" possible at all when the button is greyed out? Is the ledger lock over-engineering?*
2. *Does `record` need the settlement plan? The plan should be a read model of the current state, the payments are data.*

## 2. Answers in short
| Question | Answer |
|---|---|
| Can Carol report twice? | Yes, but not through the button. The button is disabled only while its own request runs (`SettleUpTab.tsx`, `sending` ref and `disabled={busy}`). A **second tab or device keeps an old plan** and still shows "I paid". Partial payments are allowed too, so the button is meant to come back while something is left. |
| What stops the second report? | The **reservation** (`SettlementPaymentRepository.reserved`: PENDING, and confirmed or waived but not yet booked) together with the **debtor cap**. The second request reads the first one's PENDING row and gets 422 `PAYMENT_EXCEEDS_OPEN_DEBT`. The lock is not needed for this. |
| What does the lock add? | Only atomic check-then-insert when two requests overlap within milliseconds (`twoConcurrentRecordsOfTheSameDebtorCannotTogetherExceedTheDebt` forces this with a latch). It is also group-wide, so it serialises different debtors, and it makes `record` and the booking of an expense or confirmation (`GroupLedgerService.book`, lines 82-83) exclude each other. |
| Is the lock justified? | **Yes, as cheap atomicity.** It is two lines (`createIfAbsent`, `findForUpdate`, `SettlementPaymentService` 173-174), reuses the row the booking already locks, and is held for a few queries and the plan computation (about 3 ms at 16 people, money-flow section 14.3). No deadlock path: every writer takes the one ledger row first, confirm and reject never touch it, the reads take no row locks. |
| Is the harm without it big? | No. Even an over-reservation never reaches the ledger without the creditor: R16 means Alice confirms each payment and would reject the duplicate. The lock protects one guard (R17) in a millisecond window behind a second guard (the creditor). |
| Does `record` need the plan? | **No.** The pair cap (the plan edge) is the third of three caps. The two person caps already forbid overpaying, paying a debtor and a double report. |

## 3. Why the pair cap is accidental complexity
The pair cap restricts exactly two things: (a) you may only pay a partner the plan **currently** pairs you with, (b) on a plan pair you may not pay more than that edge.

1. It validates **facts against a suggestion.** Balances are pooled; "Carol owes Alice" does not exist in the data, it is an output of the plan. The plan depends on tie-breaking by user id and on the switch to greedy above 16 people. Changing `EXACT_LIMIT` or the tie rule would silently change which real payments the server accepts.
2. It contradicts the principle of money-flow section 8: *the ledger records facts.* If Carol paid Bob in cash because that was convenient, the server refuses to record it unless the plan pairs them. A1 was withdrawn for the same reason.
3. It is **not** what makes A3 necessary. In the section 9 example (Bob reserved towards Alice, the plan now says Bob pays Carol) the debtor cap already gives 0. `payableAmount` stays necessary either way.
4. `record` and `waive` run the plan search under the group lock on every call; the payment use case depends on the plan algorithm for this one cap only.

**Stale text to fix whatever is decided:** D3 (`docs/specs/2026-11-02-settlements/design.md`) and the `OpenDebtPolicy` Javadoc justify the person caps with "no balance ever crosses zero even when the plan changes later". Money-flow section 8 says that is **not** an invariant. Also money-flow section 6.2 says "two reports of the same debtor wait for each other"; the lock is group-wide, and the realistic two-tab case is handled by the reservation.

The coupling is **logical only**: plan, payment and ledger are all inside `settlement`, no module boundary is crossed. For the service split it does not matter. The real hazard when splitting is elsewhere: `reserved` joins `LedgerEntry` of another aggregate (decision D5), and the synchronous `GroupMembership` calls.

## 4. Options
| | What changes | Disappears | Appears | Size |
|---|---|---|---|---|
| **B1 (recommended by the reviewer)** | `openDebt` (used by `record` and `waive`) uses the two person caps only. `payableEdges` keeps `min(edge - reservedOnPair, debtorCap, creditorCap)` for the screen hint. | "pair without an edge", "vanished edge clamps to zero", the dependency on the algorithm limit and tie rule, the plan search under the lock | the API accepts payments off the plan or above the edge; the screen hint is stricter than the server (`payableAmount` then means "the screen never offers more than the server accepts") | about 15 lines in `OpenDebtPolicy`, about 3 tests, D3 and money-flow text. No migration, no contract change. |
| B2 | B1, with `payableAmount` documented as a hint | same | same | docs only on top of B1 |
| B3 | drop the lock | 2 lines, 1 DB test | a millisecond window; the creditor's confirm is the backstop | not recommended: saves almost nothing, booking keeps the lock anyway |

R14 to R17 hold in all options. The UI still offers "I paid" only on plan edges, so users still follow the plan; an off-plan payment would need a new screen.

## 5. Recommendation and decision
- **Lock: leave it as is.** Correct the explanation (reservation handles tabs and retries; the lock makes check-and-insert atomic).
- **Plan in validation: B1**, after the user decides, because it changes what the API accepts.
- **Either way:** fix the "no balance crosses zero" justification (D3, `OpenDebtPolicy` Javadoc).

**Decision (user, 09.11.26):** plan edge dropped as a cap in `record` and `waive` (option B1): the server accepts up to the smaller of the debtor's debt and the creditor's credit, the screen keeps offering only plan edges ("it only shows what can be chosen, so it still works"). Done in `OpenDebtPolicy`, the contract descriptions, money-flow section 6.2 and the earlier design docs. **Lock: kept** (user, 09.11.26). Reason: it makes check-and-insert one step for the millisecond overlap, costs two lines and a few milliseconds, and the booking of an expense takes the same ledger row anyway. The reservation, not the lock, is what stops the realistic two-tab double report; money-flow section 6.2 now says so.

## 6. Triage notes (main session)
- Verified against the code: the frontend guard, the reservation query, the debtor cap producing 0 in the section 9 case, `OpenDebtPolicy` Javadoc lines 20-21 versus money-flow section 8, the lock in `SettlementPaymentService` and `GroupLedgerService`.
- Two of the reviewer's line citations were wrong (`SettlementPaymentService` 266-270 and `GroupLedgerService` 188-189 do not exist; the lock is at 173-174 and 82-83). The reasoning behind them holds.
- Not checked: the exact interleavings of two concurrent transactions under READ COMMITTED beyond the existing latch test.
