# Expense correction and removal (J5, J6)

Date: 28.11.26. Rules: R5 to R8, R10, R18, L2, L3. Diagram operations: `Expense.correct`, `Expense.remove`; events
`ExpenseCorrected`, `ExpenseRemoved`; ledger operations `appendCorrectionReversal`, `appendRemovalReversal`; the team
class diagram's `ExpenseRevision` (previous amount, previous shares, corrected at, corrected by, reason). Builds on
`feature/group-lifecycle` (a closed group already refuses expense changes through `GroupMembershipView.isOpen`).

## What is built

| Piece | Change |
|---|---|
| `Expense.correct(view, correction, actor, now)` | replaces amount, title, date, payer and shares; keeps the old version as an `ExpenseRevision`; status CORRECTED; returns `ExpenseCorrected` |
| `Expense.remove(view, actor, now)` | status REMOVED (soft delete, the expense stays visible); returns `ExpenseRemoved` |
| `ExpenseRevision` | entity of the `Expense` aggregate, one per correction, with the previous version and who, when, why |
| Ledger | `appendCorrectionReversal` and `appendRemovalReversal` book the difference, so balances follow (R8, R14) |
| HTTP | `PUT /groups/{groupId}/expenses/{expenseId}` (correct, same body as recording plus `reason`), `DELETE /groups/{groupId}/expenses/{expenseId}` (remove); `Expense` gains `revisions` |
| Notification | `EXPENSE_CORRECTED` (R7) and `EXPENSE_REMOVED` for everybody involved in the old or new version, except the person who did it |
| Frontend | "Correct" and "Remove" on an expense for the recorder, the payer and admins while the group is open; a "Corrected" badge with the earlier versions; a removed expense struck through with a "Removed" badge |

## Decisions

| # | Decision | Why / concept | Who |
|---|---|---|---|
| E1 | Everything that recording takes can be corrected: title, amount, date, payer, participants, split strategy, plus an optional reason (max. 200 characters). The recorder stays the recorder. | J5 "change the amount, payer, participants or split"; one request shape for recording and correcting | (call) |
| E2 | The new version follows the rules of recording (R1 to R4, R18): every participant and the payer must be **active** members, so a late joiner can be added (R10). | R10 "unless an existing expense is explicitly corrected to include them" | rule |
| E3 | An expense whose current version involves a former member (as payer or participant) cannot be corrected or removed: `FORMER_MEMBER_INVOLVED` (409). | The person left at balance 0 (R11); a change would give them a balance they can no longer see or settle | user, 28.11 |
| E4 | Only the recorder, the payer or an admin may correct or remove (R6): otherwise `NOT_ALLOWED_TO_CHANGE_EXPENSE` (403). A removed expense cannot be changed again: `EXPENSE_REMOVED` (409). | R6; removal is final in the MVP ("restore" is a Could) | rule |
| E5 | The ledger books the **difference**, as `EXPENSE_PAID` and `EXPENSE_OWED` entries with the correction's or removal's own id as `referenceId` (one entry per person and type, the net change; nothing for a change of 0). The diagram's single `EXPENSE_REVERSAL` type is not used. | The unique constraint `(reference_id, user_id, entry_type)` rules out the expense id; one reversal type cannot tell paid from owed for a payer who is also a participant (note in design 2026-11-23-ui-journey, A1). Each event carries the old and the new shares, so booking needs no lookup and the order of delivery does not matter: the sums commute (eventual consistency). | follows the earlier note |
| E6 | `ExpenseCorrected` carries `revisionId` (the booking reference), the previous and the new `paidBy`, `totalAmount` and `shares`, `correctedBy`, `reason`. `ExpenseRemoved` carries `removalId`, the removed version's `paidBy`, `totalAmount`, `shares`, and `removedBy`. | Events carry what consumers need, so nobody calls back (ids and value objects only) | (call) |
| E7 | Every revision is shown: the expense response lists `revisions` (oldest first), each with the previous version and who, when and why. | User, 28.11: badge plus history list | user, 28.11 |
| E8 | Notifications go to the union of payer and participants of the old and the new version, minus the actor. | R7 "the participants are notified"; a person dropped from the split must hear it too | (call) |

## Error order

`correct`: caller is a member (`GROUP_NOT_FOUND`, `NOT_A_MEMBER`), expense exists in this group
(`EXPENSE_NOT_FOUND`, 404), group OPEN (`GROUP_NOT_OPEN`, R5/L3), not removed (`EXPENSE_REMOVED`), caller may change it
(`NOT_ALLOWED_TO_CHANGE_EXPENSE`, R6), no former member in the current version (`FORMER_MEMBER_INVOLVED`), then the
checks of recording for the new version (payer, participants, recorder, amount, sum).

`remove`: the same up to `FORMER_MEMBER_INVOLVED`.

The expense row is locked while it is changed, so two corrections at the same moment are serialized and both become
revisions in order.

## Mallorca check (verified by script)

The dinner of the reference example is corrected from 180 to 200 (shares 45 → 50 for A, B, C, D, Bob paid). The ledger
books `EXPENSE_PAID +20` for Bob and `EXPENSE_OWED −5` for each of the four; the sum of the booking is 0 and Bob's
balance changes by +20 − 5 = +15, everybody else's by −5.

## Not in this change

Restoring a removed expense (Could), comments, editing the recorder.
