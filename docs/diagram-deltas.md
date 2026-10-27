# Diagram deltas: what the code has that the diagram does not show (yet)

The code follows the diagram `work/ShareWise_Domain_Model.drawio` (study repo) for every name and field it implements. This list is what is different after the alignment of 27.10.26. Each row is either a **diagram update to make** (the code is right) or a **known gap** (the diagram is right, the code comes later). Update the diagram in the same sitting; a code name the diagram lacks is a defect in one of the two.

## Diagram updates to make (code is right)

| # | Where | Diagram says | Code does | Why |
|---|---|---|---|---|
| 1 | `Expense` | no date | `date: LocalDate` (expense date) | journeys J4 and the UI need it |
| 2 | `Invitation` | `email: Email`, accept by `InvitationId` | adds `token` (the link secret); `email` is optional (null for a link invitation); a link invitation stays PENDING and admits several people, an email invitation becomes ACCEPTED when used | decision D-B: links work without a mail server |
| 3 | `GroupLedger` | attribute `groupLedger: List<LedgerEntry>` | `entries` | the attribute shared the class name |
| 4 | `LedgerEntry` | `referenceId: String` | `referenceId: UUID` | it always references an expense or payment id |
| 5 | `Currency` | value object with `code` | `java.util.Currency` | no reason to wrap the JDK type |
| 6 | `Money` | `amount: BigDecimal` | same, with scale fixed to the currency's decimals (EUR: 2); JSON as decimal string `"900.00"`; `minorUnits()` for splitting | exact arithmetic, JavaScript-safe |
| 7 | `Expense.record(...)` | `record(view, groupId, title, paidBy, recordedBy, totalAmount, shares, splitStrategy): ExpenseRecorded` | `groupId` comes from the view; extra parameters `date`, `now`; returns `Recorded(expense, event)` | one source for the group id; time is a parameter |
| 8 | Operations returning events | return the event | return a small holder: `Creation(group, groupCreated, creatorJoined)`, `Invited(invitation, event)`, `Recorded(expense, event)`, `Registration(account, event)` | an operation can produce more than one event, and the caller needs the new object too |
| 9 | `Group.createGroup` | `GroupCreated` | also returns `MemberJoined` for the creator | the creator is a member from the first moment |
| 10 | `Account.login` | `boolean` | returns `AccountEvent`: `LoginSucceeded` or `LoginFailed` | a failed attempt is counted and must be saved, so it cannot throw |
| 11 | Events | no time | every event ends with `occurredAt: Instant` | audit, ordering |
| 12 | `GroupMembershipView` | local copy updated from group events | snapshot built by the `GroupMembership` port with one fresh query when an expense is recorded; same shape and same use as drawn | decision D-A: no race between "joined" and "first expense"; can become the event-fed copy later without touching the domain |
| 13 | Account context | `Account`, tokens, `Hasher`, `EmailSender` | adds `AuthSession` (bearer-token session, only its SHA-256 is stored), `CurrentUser` (the caller of the HTTP request), `SessionIdentityFilter`; `UserProfile` is served by the port `UserProfiles` | sign-in needs a session; they are infrastructure, not part of the `Account` aggregate |
| 14 | `ExpenseShare` | value object of Expense | lives in `expense.api` (events carry it); stored through an internal `StoredShare` | `api` may not import `domain` |

## Known gaps (diagram is right, code comes later)

- Group: `declineInvitation`, `promoteToAdmin`, `removeMember`, `leaveGroup`, `closeForSettlement`, `reopenGroup`, `archiveGroup`, `updateBalanceStatus`, and their events; the enums already have all their values.
- Expense: `correct`, `remove`, the `EXACT` and `PERCENTAGE` split strategies, `ExpenseCorrected`, `ExpenseRemoved`.
- Settlement: `appendCorrectionReversal`, `appendRemovalReversal`, `appendSettlementPayment`, `appendDebtWaiver`, `projectSimplifiedPlan`, the `SettlementPayment` aggregate and its events, `MemberBalanceStatusChanged`, `AllBalancesSettled`.
- Account: email verification, password reset, `changePassword`, `changeDisplayName`, `deactivate`, lock-out after five failed attempts (the counter exists), `AccountToken`, `EmailSender`.
- Invitations by email and the in-app invitation inbox (decision D-B).
