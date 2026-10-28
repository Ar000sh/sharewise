# Design: conventions and alignment with the domain diagram

**Date:** 27.10.26 · **Status:** implemented on branch `refactor/diagram-alignment` (27.10.26); what differs from the diagram is in `docs/diagram-deltas.md` · **Rules:** `docs/conventions/`

## 1. Why
The Ex4 build was done by four parallel sessions. It works (124 backend tests, 73 frontend tests, 2 browser tests), but it has four dialects: `expense` and `settlement` have `domain/application/persistence/web`, `group` and `account` are flat; `Member` / `MemberId` / `UserId` name the same person; the diagram shown to the professor says `Membership`, `GroupRole`, `ExpenseShare`, `SplitStrategy`, `paidBy`, `totalAmount`, while the code says `Member`, `Role`, `Share`, `SplitType`, `payerId`, `amountCents`. We need one set of rules, enforced by tests, and the code brought in line with the diagram before Ex5 piles features on top.

## 2. Goals / non-goals / anti-goals
**Goals**
1. Written rules (`docs/conventions/01–04`) that settle layout, vocabulary, tests, comments, git, frontend.
2. The rules are checked by the build (`ModularityTests`, new `ConventionTests`, formatter check, ESLint).
3. Code, database, HTTP contract and frontend use the diagram's names and shapes, including `Money` as `BigDecimal` + `Currency`.
4. Behaviour stays identical: the same user journeys, the same results (Mallorca: Alice +540,00 / Bob −180,00 / Carol −360,00 in the browser test).

**Non-goals** (stay in Ex5 or later): exact and percentage splits, correct/remove expense, leave/remove/promote, decline/expiry handling beyond today, close/reopen/archive, settlement payments and plan, email verification, password reset, account lock-out, Keycloak, `GroupMembershipView` fed by events (see D-A).

**Anti-goals:** a refactor that changes behaviour; renaming in code but not in the contract/frontend (that is the dialect problem again); a diagram that no longer matches the code after the change.

## 3. Decisions
| # | Decision | By |
|---|---|---|
| R1 | Code follows the diagram's names. The API follows them too (JSON fields renamed), so there is one vocabulary from database to button label. | you (27.10) |
| R2 | `Money` is `BigDecimal amount + Currency`, as drawn. Database `NUMERIC(19,2)`. | you (27.10) |
| R3 | One module layout everywhere: `api/ domain/ application/ persistence/ web/`; JPA annotations allowed on aggregates; aggregates return their events, services publish them. | you (27.10) |
| R4 | JSON money is a **decimal string** `"900.00"`, not a number, so JavaScript never turns it into a float. The frontend converts at one place (`lib/money.ts`). | my call |
| R5 | `UserId` is the only person identity; `MemberId` is deleted. `Membership` is the entity holding a `UserId` + `GroupRole`. | my call, follows the diagram |
| R6 | The behaviour of Ex4 stays; operations the diagram has but Ex4 lacks are *not* added. Names/fields the diagram has for things that exist today are added (e.g. `Membership.status`, `Invitation.status`, `Expense.status`, `recordedBy`, `Account.status = ACTIVE`). | my call |
| R7 | Sessions (`auth_session`, bearer token) are application/infrastructure, not part of the `Account` aggregate; the diagram gets a note. | my call |
| R8 | The work is done **in one sequence by one session** (no parallel tracks): a rename touches every layer, and four editors would recreate today's problem. Each step ends green and committed on branch `refactor/diagram-alignment`. | my call |

### Decided by you on 27.10
- **D-A (Expense learns the group through a view): my recommendation. SUPERSEDED on 28.10 by `2026-10-28-membership-copy-decision.md`: the view is now an event-fed copy owned by Expense, not a snapshot from the port.** `Expense.record(GroupMembershipView view, ...)` has the diagram's signature and the rules R2/R3/R5/R6 live in the aggregate. The view is a snapshot (`isOpen`, `activeMembers`, `admins`) that the `GroupMembership` port builds with one fresh query when the expense is recorded. An event-fed local copy can replace the source later without touching the domain.
- **D-B (Invitations): identified by email, token kept.** `Invitation` gets `id: InvitationId`, `email: Email`, `status: InvitationStatus` and keeps the `token` link secret. Delivery: either an email provider, or (preferred, no provider needed) an **in-app notification inbox** where the invited person, signed in with that email, sees pending invitations and accepts or declines. Both are **follow-up features after this refactor** (they change behaviour: `GET /invitations` for the caller's pending invitations, accept by `InvitationId`, only the invited email may accept). In this refactor the fields exist and the email is optional, so today's link invitations keep working unchanged.
- Sending real email later = a port `InvitationNotifier` in `group.api` with an in-app implementation first and an SMTP implementation behind the same port.

## 4. Rename map (old → new)
| Area | Old | New |
|---|---|---|
| shared | `MemberId`, `UserId`, `GroupId`, `Money(long cents, Currency)` | `UserId` only; `GroupId`; `Money(BigDecimal amount, Currency currency)`; new `Email` |
| account | `User`, `UserRepository`, `UserInfo`, `UserDirectory` | `Account`, `AccountRepository`, `UserProfile` (record), `UserProfiles` (port with `findProfiles(Set<UserId>)`); value objects `DisplayName`, `PasswordHash`, `RawPassword`; domain service `Hasher`; events `AccountRegistered`, `LoginSucceeded`, `LoginFailed`; `AuthService` → `AccountService` |
| account | `AuthController`, `AccountController`, `SessionIdentityFilter`, `AuthSession` | `AccountController` (`/auth/*`, `/me`), `SessionIdentityFilter`, `AuthSession` (application/persistence, R7) |
| group | `Group`, `Member`, `Role`, `Invitation` (String fields) | `Group` (`GroupName`, `Currency`, `GroupStatus`), `Membership`, `GroupRole`, `MembershipStatus`, `Invitation` (`InvitationId`, `Email?`, `InvitationStatus`, `expiresAt`) |
| group | `Group.create`, `invite`, `acceptInvitation` | `createGroup`, `inviteMember`, `acceptInvitation` (each returns its event) |
| group | `MemberJoined(groupId, MemberId, role)`, `GroupCreated(groupId, UserId)` | `MemberJoined(groupId, userId, role)`, `GroupCreated(groupId, creator)`, new `MemberInvited` |
| group | `GroupMembership` port | stays; returns/consumes `UserId`; adds `GroupMembershipView view(GroupId)` (D-A) |
| expense | `Share`, `SplitType`, `payerId`, `amountCents`, `RecordExpenseCommand` | `ExpenseShare(participantId, owedShare)`, `SplitStrategy`, `paidBy`, `recordedBy`, `totalAmount`, `status = ACTIVE`, `RecordExpenseCommand` (fields renamed) |
| expense | `ExpenseService.record` | `ExpenseService.recordExpense` → `Expense.record(view, …)` returns `ExpenseRecorded` |
| expense | `ExpenseRecorded(expenseId, groupId, payerId, totalCents, shares)` | `ExpenseRecorded(expenseId, groupId, paidBy, totalAmount, shares: List<ExpenseShare>)` |
| settlement | `GroupLedger.entries`, `LedgerEntry(memberId, deltaCents, referenceId)`, `Balances.of`, `MemberBalance(memberId, displayName, balanceCents)` | `entries` (diagram delta), `LedgerEntry(id: EntryId, userId, deltaAmount, …)`, `Balances.of(GroupId)`, `MemberBalance(userId, netBalance)`; `displayName` is added by the controller from `UserProfiles` |
| database | `members`, `*_cents`, `payer_id`, `member_id` | `memberships`, `NUMERIC(19,2)` columns without `_cents`, `paid_by`, `user_id`; done by new Flyway migrations, existing ones untouched |
| HTTP | `payerId`, `amountCents`, `shareCents`, `memberId`, `balanceCents`, `split`, `role` | `paidBy`, `totalAmount` (string), `owedShare` (string), `userId` / `participantId`, `netBalance` (string), `splitStrategy`, `role: GroupRole` |

## 5. Diagram deltas (the code needs these, the diagram lacks or spells differently)
We update the diagram, in the same series of commits, for:
1. `Expense.date` (expense date) — the diagram has no date; journeys J4 and the UI need it.
2. `Invitation.token` (link secret) and `Invitation.email` optional (D-B).
3. `GroupLedger.groupLedger: List<LedgerEntry>` → `entries` (the attribute shares the class name).
4. `LedgerEntry.referenceId: String` → `UUID` (it references an expense or a payment id).
5. `CurrentUser` port and `AuthSession` (R7), `SessionIdentityFilter` as account infrastructure.
6. `Money.amount` note: scale = currency fraction digits; JSON as decimal string.
7. `GroupMembershipView` source note (D-A).

## 6. Work plan (each step ends with green checks and its own commit)
0. Review and approve this document and `docs/conventions/` (**you**). Then commit them to `main`.
1. **Tooling first:** add the formatter plugin and `ConventionTests`; the rules start enabled only for modules already refactored, a module is added to the list as its step finishes. Remove Lombok.
2. **shared:** `UserId` only, `Money` with `BigDecimal`, `Email`, error codes. All modules adapt compile-wise.
3. **account**, 4. **group**, 5. **expense**, 6. **settlement:** layout, names, value objects, aggregates returning events, Flyway migration, tests moved/renamed to the rules (fixtures, test names, vocabulary).
7. **Contract + frontend:** `openapi.yaml` (own commit), `npm run gen:api`, frontend renames, `lib/money.ts` for decimal strings, component/route tests, Playwright test.
8. **Close-out:** `ConventionTests` for all modules, `RUNNING.md`, diagram updated (study repo), `CLAUDE.md` status, merge to `main`, push.
9. After the merge, every new piece of work (Ex5 first) starts from these rules.

Estimated effort: a long working session; every one of the 124 backend tests is touched by the renames. Risk is contained by the order (module by module), the Mallorca slice test and the browser test as the behaviour guard.

## 7. Left to the builder
Exact ArchUnit rule wording, the formatter plugin version, the internal shape of `Hasher`, how `MutableClock`/fixtures are renamed, and the order of fields in records.
