# Diagram deltas: what the code has that the diagram does not show (yet)

The code follows the diagram `docs/domain/ShareWise_Domain_Model.drawio` for every name and field it implements. This list is what is different after the alignment of 27.10.26. Each row is either a **diagram update to make** (the code is right) or a **known gap** (the diagram is right, the code comes later). Update the diagram in the same sitting; a code name the diagram lacks is a defect in one of the two.

## Diagram updates to make (code is right)

| # | Where | Diagram says | Code does | Why |
|---|---|---|---|---|
| 1 | `Expense` | no date | `date: LocalDate` (expense date) | journeys J4 and the UI need it |
| 2 | `Invitation` | `email: Email`, accept by `InvitationId` | `invitee: UserId` (the existing account it is for) replaces the email; there is no token and no link; only the invitee can accept or decline; `EXPIRED` is a diagram status that is derived from `expiresAt` (`Invitation.statusAt(now)`), never stored; `MemberInvited` carries `invitee`, `MemberJoined` carries `invitedBy` (null for the group creator), `InvitationDeclined` carries `invitationId`, `invitee`, `invitedBy` | design 2026-11-03 N2/N10: invitations go to existing accounts found by exact username or email; supersedes the link invitation of decision D-B |
| 3 | `GroupLedger` | attribute `groupLedger: List<LedgerEntry>` | `entries` | the attribute shared the class name |
| 4 | `LedgerEntry` | `referenceId: String` | `referenceId: UUID` | it always references an expense or payment id |
| 5 | `Currency` | value object with `code` | `java.util.Currency` | no reason to wrap the JDK type |
| 6 | `Money` | `amount: BigDecimal` | same, with scale fixed to the currency's decimals (EUR: 2); JSON as decimal string `"900.00"`; `minorUnits()` for splitting | exact arithmetic, JavaScript-safe |
| 7 | `Expense.record(...)` | `record(view, groupId, title, paidBy, recordedBy, totalAmount, shares, splitStrategy): ExpenseRecorded` | `groupId` comes from the view; extra parameters `date`, `now`; returns `Recorded(expense, event)` | one source for the group id; time is a parameter |
| 8 | Operations returning events | return the event | return a small holder: `Creation(group, groupCreated, creatorJoined)`, `Invited(invitation, event)`, `Recorded(expense, event)` | an operation can produce more than one event, and the caller needs the new object too |
| 9 | `Group.createGroup` | `GroupCreated` | also returns `MemberJoined` for the creator | the creator is a member from the first moment |
| 10 | `Account` (external identity provider variant) | `registerFromIdentityProvider(UserId, Email, DisplayName)` | same, plus `now`; there is no `login`, no `passwordHash`, no `LoginSucceeded`/`LoginFailed` | sign-in belongs to Keycloak (design 2026-10-30); the account is created on the first request of a new subject |
| 11 | Events | no time | every event ends with `occurredAt: Instant` | audit, ordering |
| 12 | `GroupMembershipView` | local copy updated from group events | snapshot built by the `GroupMembership` port with one fresh query when an expense is recorded; same shape and same use as drawn. The event-fed copy exists on branch `feature/expense-membership-copy` (P3 step, see `docs/notes/2026-10-28-membership-copy-decision.md`) | decision D-A: no race between "joined" and "first expense"; can become the event-fed copy later without touching the domain |
| 13 | Account context | `Account`, the external identity provider | adds `CurrentUser` (the caller of the HTTP request, from the validated Keycloak token) and `JwtCallerConverter`; `UserProfile` is served by the port `UserProfiles` | infrastructure, not part of the `Account` aggregate |
| 14 | `ExpenseShare` | value object of Expense | lives in `expense.api` (events carry it); stored through an internal `StoredShare` | `api` may not import `domain` |
| 15 | `Account`, `UserProfile` | no username | `Account.username: Username` (set from the Keycloak `preferred_username` on first login, unique, never contains `@`); `UserProfile.username`; the port `UserProfiles` gains `findByIdentifier(String)` | invitations find an existing account by exact username or email (design 2026-11-03, N3/N4); the diagram was updated with this change |
| 16 | Notification context | no such context | new context `Notification`: aggregate root `Notification` (ids only), enumeration `NotificationType`; consumes `MemberJoined` and `InvitationDeclined`, and since 04.11 `SettlementPaymentConfirmed`, `SettlementPaymentRejected` and `DebtWaived` (types `PAYMENT_CONFIRMED`, `PAYMENT_REJECTED`, `DEBT_WAIVED`; the planned `PAYMENT_TO_CONFIRM` is dropped), and since 09.11 `ExpenseRecorded` (type `EXPENSE_RECORDED`; the event gained `recordedBy`, which the diagram does not draw because it lists event names only); the diagram was updated with these changes | design 2026-11-03 N1/N7: the inbox shows things that happened; a pending invitation or a payment waiting for an answer is asked from its context instead (design 2026-11-04 I1/I2) |
| 17 | `GroupDirectory` | no such port | read model port in `group.api`: `findNames(Set<GroupId>)` | the inbox shows group names without copying them |
| 18 | `GroupLedger.append*` | `appendExpenseEntry`, `appendSettlementPayment`, `appendDebtWaiver` return `MemberBalanceStatusChanged, AllBalancesSettled` | the append operations return nothing; `GroupLedgerService` compares the balance totals (a database query) before and after the booking and publishes one `MemberBalanceStatusChanged` per flipped person and `AllBalancesSettled` when everybody is at zero | the ledger would have to load every entry to know the old and new zero-states; the query reads only the totals (see `GroupLedger.projectBalances` Javadoc) |
| 19 | `MemberBalanceStatusChanged`, `AllBalancesSettled` | drawn among `SettlementDomainEvents` | published by settlement, but live in `shared.events`, not in `settlement.api` | the one exception to "events live in the publisher's `api`" (D12, convention 01 section 5.1): group consumes the first, and a group listener for a `settlement.api` type would make the module graph cyclic. The diagram keeps them under settlement because it shows who publishes, not the Java package |
| 20 | `Invitation`, `MemberInvited`, `MemberPromoted` | `Invitation` has no role; `MemberInvited` has no role | `Invitation.role: GroupRole` (MEMBER by default) is applied when the invitee accepts; `MemberInvited.role`; `Group.inviteMember(invitee, inviter, role, now)`; only an admin may invite; `promoteToAdmin(user, promotedBy, now)` returns `MemberPromoted(groupId, userId, promotedBy, occurredAt)`; promotion is one way | design 2026-11-09: admins decide who joins and with which role; the diagram needs the field and the event's fields |
| 21 | `NotificationType` | `MEMBER_JOINED`, `INVITATION_DECLINED` | adds `MEMBER_PROMOTED` (consumes `MemberPromoted`, recipient the promoted person) | design 2026-11-09, A6 |

## Deliberate changes made in the diagram (settlements, 03.11.26)

The team version of 17.10.26 drew these differently; the diagram now shows what the code does.

| # | Where | Team version said | Now (diagram and code) | Why |
|---|---|---|---|---|
| S1 | `SettlementPayment.waive` | instance operation `waive(UserId creditor, String note): DebtWaived` on an existing payment | a factory like `record`: `waive(GroupId, UserId creditor, UserId fromDebtor, Money amount, Money openDebt, String note): DebtWaived`; the new payment is WAIVED from the start (code: plus `now`, returns `Waived(payment, event)`, see row 8) | a creditor forgives a debt without any payment having been reported first, so there is no PENDING payment to call `waive` on (R17, design D8) |
| S2 | `SettlementPayment` fields | `id`, `groupId`, `fromDebtor`, `toCreditor`, `amount`, `status` | adds `note: String`, `createdAt: Instant`, `resolvedAt: Instant`; `record` takes an optional `note` | the contract shows the debtor's note, the rejection or waiver reason, and when a payment was reported and answered |
| S3 | `Group.updateBalanceStatus` | `updateBalanceStatus(UserId member, boolean hasZeroBalance)` | adds `Instant occurredAt` (the event's time) | `MemberBalanceStatusChanged` arrives asynchronously and possibly out of order; an older event must not overwrite a newer one, or a member with debts could leave (R11) |
| S4 | `Membership` | no time for the balance flag | adds `balanceStatusAt: Instant` (column `balance_status_at`, migration `group/V204`, null before the first event) | where S3 stores the time of the last applied event; equal times apply, so a frozen test clock still works |

## Known gaps (diagram is right, code comes later)

- Group: `removeMember`, `leaveGroup`, `closeForSettlement`, `reopenGroup`, `archiveGroup`, and their events; the enums already have all their values.
- Expense: `correct`, `remove`, the `EXACT` and `PERCENTAGE` split strategies, `ExpenseCorrected`, `ExpenseRemoved`.
- Settlement: `appendCorrectionReversal`, `appendRemovalReversal` (they follow `correct` and `remove` of the expense context). `AllBalancesSettled` is published but has no consumer yet (auto-archive L5 is parked).
- Account: `changeDisplayName`, `deactivate` (email verification, password reset and lock-out are Keycloak's).
- Notification context (the in-app inbox): built with the `notification` module of design 2026-11-03.
