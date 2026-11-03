# Design: usernames, targeted invitations and the notification inbox

**Date:** 03.11.26 · **Status:** draft, all open decisions answered, waiting for approval of the whole document · **Branch:** `feature/notifications` (from `main`) · **Next steps:** `/edge-case-probe` plan mode, `/design-review`, then the plan

Marks used: **(decided)** the user decided it in the brainstorming, **(call)** our choice taken without asking, can be overruled, **(open)** needs the user's answer before the plan.

## 1. Intent and why
Today an invitation is a link that anyone who holds it can use. The invited person never sees it inside ShareWise, and the inviter cannot know who it was for. We want what other apps (Splitwise and the like) do: you invite a **known person** by username or email, the person finds the invitation in an **inbox** inside the app (a bell with "1 new"), and accepts or declines there.

The inbox is also the first place for everything else that matters to a person: a payment to confirm, a debt waived, somebody who joined or declined. So the inbox is built as a generic module, with invitations as its first and most important entry type. A later email channel (SMTP) can be added as a second way to deliver the same notification; it is not part of this work.

Concepts this practises (for P2/P3): bounded context and context map (a new, upstream-free leaf context), eventual consistency (the inbox row appears moments after the event), idempotent consumers (a redelivered event must not create a second row), the outbox already in place (`event_publication`), and a leaf event consumer as the first candidate to extract as a service in P3.

## 2. Goals, non-goals, anti-goals
**Goals**
- Invite an existing account by exact username or exact email; the invitation belongs to that account.
- The invited person sees it in a bell inbox with an unread count and can **accept** (joins and lands in the group) or **decline**.
- The inviter learns the answer (inbox entry).
- Further entry types plug in by adding one listener and one text, without touching the inbox core.
- Accounts get a unique, human-readable username.

**Non-goals (this work)**
- Email, SMS or push delivery. (The design leaves a seam; see 6.5.)
- Notifications for expenses (recorded, corrected, removed): too noisy, the expense list shows them. **(decided)**
- A full inbox page with history and filters (the data model allows it; the first UI is the dropdown). **(decided: dropdown)**
- Real-time push (WebSocket/SSE); the bell polls. **(call)**
- Changing the username after sign-up inside ShareWise.
- Inviting people who have no account yet, and invitation links for anyone. **(decided: removed)**
- Fuzzy search or autocomplete of usernames (see anti-goals).

**Anti-goals (a failure even if it works)**
- A search that lets someone list or browse accounts. Lookup is exact match, one account at a time.
- The notification module becoming a dependency of group or settlement (it must stay a leaf; the module graph stays one-way).
- Notification text or names stored in events or copied into other modules (events carry ids only).
- Two sources of truth for an invitation's state: the inbox row only points at the invitation, the state lives in `group`.

## 3. Decisions taken in the brainstorming
| # | Decision | Source |
|---|---|---|
| N1 | The inbox is its own module `notification`, a leaf nobody depends on. | recommended, accepted |
| N2 | Invitations go only to existing accounts; the link-for-anyone invitation is removed. | decided |
| N3 | Lookup key: exact **username or email**. | decided |
| N4 | Usernames are owned by **Keycloak**: the realm stops using the email as username (`registrationEmailAsUsername` false); on first login `Account.username` is copied from `preferred_username` (same pattern as email and display name, D10/C2). | decided (option A) |
| N5 | Login accepts username **or** email, always with the password (`loginWithEmailAllowed` stays true). | decided |
| N6 | The inbox is a bell with a dropdown (option A of the mockup). Accept and Decline sit on the invitation entry. | decided |
| N7 | Entry types: invitation received; joined / declined (to the inviter); payment recorded (to the creditor); payment confirmed / rejected (to the payer); debt waived (to the debtor); member removed. Expenses are excluded. | decided ("do all") |
| N8 | Base is `main`. The payment, waiver and removal entries are built last, after `feature/settlements` is merged into `main` (the settlement events exist only there; `MemberRemoved` and `InvitationDeclined` exist nowhere yet). | decided |

## 4. Usernames (account context)
- `Account` gets `username` (value object `Username`, in `account.domain`; it appears in a port record only as plain text, like `DisplayName`). Stored **lower case**, unique (`UNIQUE` index on the column).
- Rule for `Username` **(call)**: 3 to 30 characters of `a-z 0-9 . _ -`, no `@`. Why no `@`: the lookup tells an email from a username by the `@`. If a username could look like an email, a person could register the username `bob@example.com` and receive the invitations meant for the real Bob.
- The Keycloak realm must enforce the same rule at sign-up (a validator on the username attribute in the realm's user profile), so the sign-up form rejects a bad name instead of ShareWise failing on first login (the lesson of C7). `editUsernameAllowed: false`. **(call)**
- First login: take `preferred_username` as it is. There is **no legacy handling**: the dev database and the Keycloak realm are deleted and created fresh, so every account is made under the new realm rules **(decided)**. A token whose username is not a valid `Username` is refused with a clear error instead of being repaired.
- The sign-up form therefore asks for username, email, first name, last name, password.
- `UserProfile` gains `username` (plain text); `GET /me` returns it.
- New port method `UserProfiles.findByIdentifier(String identifier)`: `Optional<UserProfile>`, works without a transaction, matches exact and case-insensitive; an identifier with an `@` is an email, otherwise a username; an inactive account is not found.
- Migration `V106__account_username.sql` (never edit an existing one): add the column as `NOT NULL` with a `UNIQUE` index (no backfill, the database starts empty). It is still a new migration, because a migration that is on `main` is never edited.

## 5. Targeted invitations (group context)
**Model change** (diagram delta, see section 10): `Invitation` no longer carries a token and an optional email. It carries `invitee: UserId` (the account it is for). Status as in the diagram: `PENDING, ACCEPTED, DECLINED, EXPIRED`.

**Operations on the `Group` root** (each returns its event; the service stays thin: load, call, save, publish):
| Operation | Rule | Event |
|---|---|---|
| `inviteMember(invitee, inviter, now)` | the inviter is an active member (`NOT_A_MEMBER`); the invitee is not an active member (`ALREADY_MEMBER`); the invitee has no `PENDING` invitation to this group (`ALREADY_INVITED`, new); valid 7 days (R9) | `MemberInvited(groupId, invitationId, invitee, invitedBy, occurredAt)` |
| `acceptInvitation(invitationId, user, now)` | the invitation is for `user` (otherwise `INVITATION_NOT_FOUND`, so nobody can probe other people's invitations), pending, not expired (`INVITATION_EXPIRED`), user not yet a member | `MemberJoined` |
| `declineInvitation(invitationId, user, now)` (new) | same checks as accept; sets `DECLINED`; nothing joins | `InvitationDeclined(groupId, invitationId, invitee, invitedBy, occurredAt)` (new) |

- `GroupService.inviteMember(groupId, inviter, identifier)` resolves the identifier through `UserProfiles.findByIdentifier` (group already depends on `account::api`) and passes the `UserId` to the domain. No account found: `ACCOUNT_NOT_FOUND` (404, new); the domain never calls another module.
- Two members inviting the same person at the same time: a partial unique index on `(group_id, invitee_id) WHERE status = 'PENDING'` makes the database the referee; the loser gets `ALREADY_INVITED`. Accept and decline lock the group row like accept does today.
- An expired invitation is never "swept": it is expired **at read time** (`expiresAt` passed), as accept does today. `EXPIRED` is shown, not stored, until something needs it stored. **(call)**
- Re-inviting after `DECLINED` or expiry is allowed. **(call)** (Spam by repeated invites is listed as a failure mode, section 9.)
- **Event shape changes:** `MemberInvited` replaces `email` with `invitee: UserId`. `MemberJoined` gains `invitedBy: UserId`, the member who sent the invitation; it is `null` when the person created the group, because nobody invited them. The notification listener creates nothing when it is `null`. The Javadoc of the record says so. **(decided O1, option A)** Rejected: a second event `InvitationAccepted` (cleaner, but one more event the diagram lacks and two messages for one action in P3).
- Migration `V204__invitations_for_accounts.sql`: add `invitee_id` as `NOT NULL` (the database starts empty, so no old link invitations need converting); drop the `token` and `email` columns (nothing reads them any more); add the partial unique index.

## 6. The `notification` module
### 6.1 Position and dependencies
New bounded context `Notification` (generic subdomain, like Account), layout as every module. Allowed dependencies: `account::api, group::api, shared`; later `settlement::api`. **Nobody depends on it.** This is a design decision (new module, new dependencies); the line goes into `package-info.java` and `docs/conventions/01`, and `ModularityTests` checks it.

Why a module and not a table in `group`: payment and waiver entries come from `settlement`, and `settlement` already depends on `group`. Notification rows inside `group` would force `group → settlement`, the cycle `ModularityTests` refuses (document 01, 5.1).

### 6.2 Model
- Aggregate root `Notification`: `id: NotificationId`, `recipient: UserId`, `type: NotificationType`, `groupId: GroupId`, `actor: UserId` (who did it), `reference: UUID` (the invitation or payment it points at), `occurredAt`, `readAt` (null = unread).
- Operations: `Notification.notify(...)` (factory) and `markRead(now)` (idempotent: reading twice keeps the first time).
- `NotificationType` starts with `INVITATION_RECEIVED`, `MEMBER_JOINED`, `INVITATION_DECLINED`; later `PAYMENT_TO_CONFIRM`, `PAYMENT_CONFIRMED`, `PAYMENT_REJECTED`, `DEBT_WAIVED`, `MEMBER_REMOVED`.
- A notification is **immutable except `readAt`**. It stores ids, never texts or names; the texts are built from the type and the looked-up names when the inbox is read (names through `UserProfiles`, the group name through a new group port, see 6.4). Why: names change, texts can be reworded or translated, and events must stay ids only (document 01).
- Table `notifications`, migration `V501__notifications.sql` (new range `V5xx`), `UNIQUE (recipient_id, type, reference)` so a redelivered event cannot create a second row (idempotent consumer); the insert uses `ON CONFLICT DO NOTHING`, the same technique as `AccountRepository.insertIfAbsent`. Index on `(recipient_id, read_at, occurred_at DESC)` for the bell.
- Nothing is sent to yourself: if `actor` equals the recipient no row is made.

### 6.3 Which event becomes which entry
| Event (publisher) | Recipient | Type | Built |
|---|---|---|---|
| `MemberInvited` (group) | invitee | `INVITATION_RECEIVED` | on `main` |
| `MemberJoined` through an invitation (group) | inviter | `MEMBER_JOINED` | on `main` |
| `InvitationDeclined` (group, new) | inviter | `INVITATION_DECLINED` | on `main` |
| `SettlementPaymentRecorded` (settlement) | `toCreditor` | `PAYMENT_TO_CONFIRM` | after settlements is merged |
| `SettlementPaymentConfirmed` / `Rejected` (settlement) | the payer | `PAYMENT_CONFIRMED` / `PAYMENT_REJECTED` | after settlements is merged |
| `DebtWaived` (settlement) | the debtor | `DEBT_WAIVED` | after settlements is merged |
| `MemberRemoved` (group) | the removed person | `MEMBER_REMOVED` | when `removeMember` exists |

One `<Event>Listener` per event, `@ApplicationModuleListener` (asynchronous, after commit, kept in the event publication registry). Adding a type later is one listener, one enum constant, one text in the frontend.

### 6.4 Ports and endpoints
- Group `api` gets one read-only port `GroupDirectory` **(call)**, which answers without a transaction: the group name for a set of group ids, and the status of a set of invitation ids (so the inbox can hide Accept/Decline on an invitation that was answered meanwhile). Unknown ids are left out. It needs an integration test without a transaction (document 02, pitfall 5).
- **When an entry counts as read** **(call)**: when the user clicks it, answers an invitation (accept or decline), or uses "mark all as read". Merely opening the dropdown does not mark anything, so an unanswered invitation keeps the badge until it is dealt with.
- **Retention** **(call)**: entries are never deleted in this version (like completed `event_publication` rows, document `2026-10-27`); a cleanup is parked.
- HTTP (contract first, `/contract-change`); every call is for the caller's own notifications only:
  - `GET /notifications?unread=true&limit=10` returns entries with `id`, `type`, `occurredAt`, `readAt`, `actor {userId, displayName, username}`, `group {id, name}`, `reference`, and for invitations the current `invitationStatus`; plus `unreadCount`.
  - `POST /notifications/{id}/read`, `POST /notifications/read-all`. Another person's id answers 404.
  - Group: `POST /groups/{groupId}/invitations` with `{ "identifier": "alice" }` (replaces the link creation), `POST /invitations/{invitationId}/accept` (replaces the token path), `POST /invitations/{invitationId}/decline`.
  - New error codes `ACCOUNT_NOT_FOUND` (404), `ALREADY_INVITED` (409), in all three places (document 03, section 3).

### 6.5 Seam for email later
Delivery channels are separate from the inbox: a later `EmailNotifier` is another listener on the same events (or on the stored `Notification`), reading the recipient's email through `UserProfiles`. The inbox stays the record; email is only another way to tell. Not built now.

## 7. Frontend
- Header: bell with the unread count; the dropdown shows the latest 10 entries; the unread count is polled (every 30 s and when the tab regains focus) **(call)**; a short delay after an invitation is expected (eventual consistency, like balances).
- Invitation entry: "**Alice** invited you to **Mallorca**" with Accept (calls accept, then goes to `/groups/{id}`) and Decline. If the invitation was answered or expired meanwhile the buttons are replaced by its status.
- Group page: the invite box takes username or email; results: sent / no account found / already a member / already invited.
- Removed: the `/join/[token]` page, `JoinGroup`, the link `InviteButton` and the matching route handlers and e2e step; replaced by the invite box and the bell.
- Texts per type live in one table, new error codes in `frontend/src/lib/errors.ts`.

## 8. Build order (one concern per commit)
1. `contract`: invitations, notifications, username, error codes (own commit).
2. Account: Keycloak realm, `Username`, migration, `UserProfiles.findByIdentifier`, `GET /me`, test helper `KeycloakUsers.create`.
3. Group: targeted `inviteMember`, `declineInvitation`, `InvitationDeclined`, migration, accept check, controller.
4. Notification module: model, migration, listeners for the three group events, endpoints, `ModularityTests` line.
5. Frontend: invite box, bell and dropdown, removal of the link flow, e2e.
6. After `feature/settlements` is merged into `main`: listeners for payment, confirmation, rejection and waiver; `MemberRemoved` when `removeMember` exists.

## 9. Failure modes (first list; `/edge-case-probe` plan mode completes it)
- Invitation answered or expired while the entry is still in an open dropdown: accept answers 404/409/410 and the UI refreshes the entry.
- The listener fails: the invitation exists but no inbox row. Redelivered on restart only (document `2026-10-27`); consequence accepted, the inviter sees the pending invitation in the group and can re-invite after it expires.
- Event delivered twice: unique constraint plus `ON CONFLICT DO NOTHING`; tested.
- Invitee deactivated, or deletes the account, after being invited.
- Repeated invitations to someone who declined (harassment): not limited in this version; parked.
- Account enumeration: "No account found" tells the inviter whether a username or address is registered. Accepted: only a signed-in member of a group can ask, and a clear message matters more here **(decided)**. No rate limit.
- Two people choosing the same username at the same moment: Keycloak refuses the second; the unique index in ShareWise is the second safety net.
- Group closed or archived with open invitations: accept follows the group status rules when those exist.
- Notification for an actor who is also the recipient: no row.

## 10. Documents that change with the code
- **Diagram** (`docs/domain/ShareWise_Domain_Model.drawio`) and `docs/diagram-deltas.md`: new context Notification (`Notification`, `NotificationType`); `Account.username`; `Invitation.invitee: UserId` replacing the email; event `MemberInvited` carries `invitee`; `MemberJoined.invitedBy`. A code name the diagram lacks is a defect, so the diagram changes in the same commit as the code.
- `docs/conventions/01` (module table, dependencies, vocabulary, "words we do not use"), `02` if a new test pattern appears, `docs/api/openapi.yaml`, `RUNNING.md` (realm change, new sign-up form), `docs/2026-10-30-keycloak-auth*.md` (username claim), the e2e flow.
- Grep for the old terms: `token`, `createInvitation`, `link invitation`, `/join/`.

## 11. Open decisions for the user
None. O1 is decided (section 5). Everything else is decided or marked (call).
