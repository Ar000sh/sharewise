# Design: usernames, targeted invitations and the notification inbox

**Date:** 03.11.26 · **Status:** v2 (design review applied), waiting for approval of the whole document · **Branch:** `feature/notifications` (from `main`) · **Next step:** the implementation plan

Marks used: **(decided)** the user decided it, **(call)** our choice taken without asking, can be overruled, **(review)** a change that came from the independent design review (section 3).

## 1. Intent and why
Today an invitation is a link that anyone who holds it can use. The invited person never sees it inside ShareWise, and the inviter cannot know who it was for. We want what other apps (Splitwise and the like) do: you invite a **known person** by username or email, the person finds the invitation inside the app (a bell with "1 new"), and accepts or declines there.

The bell is the one place for what matters to a person. It shows two kinds of things, on purpose:
- **State that needs an answer** (an invitation waiting for you). This is a *question about the current state* of the group context, so it is a query to `group` (document 01, section 5: a synchronous question is a port or query, not an event).
- **Things that happened** (Bob joined, Bob declined; later: a payment to confirm, a debt waived). These are *events*, delivered to a `notification` module that keeps an inbox row per recipient.

A later email channel (SMTP) can be added as another way to deliver the same events; it is not part of this work.

Concepts this practises (for P2/P3): bounded context and context map (a new, upstream-free leaf context), state versus event (query for state, event for what happened), eventual consistency (an entry appears moments after the event), idempotent consumers (a redelivered event must not create a second row), the outbox already in place (`event_publication`), and a leaf event consumer as the first candidate to extract as a service in P3.

## 2. Goals, non-goals, anti-goals
**Goals**
- Invite an existing account by exact username or exact email; the invitation belongs to that account.
- The invited person sees the invitation in the bell, with the unread count, and can **accept** (joins and lands in the group) or **decline**.
- The inviter learns the answer (inbox entry).
- Further entry types plug in by adding one listener and one text, without touching the inbox core.
- Accounts get a unique, human-readable username.

**Non-goals (this work)**
- Email, SMS or push delivery. (The design leaves a seam; see 6.5.)
- Notifications for expenses (recorded, corrected, removed): too noisy, the expense list shows them. **(decided)** **Changed 09.11.26:** a recorded expense now creates an entry (`EXPENSE_RECORDED`) for everybody involved except the recorder; corrected and removed expenses still do not (the operations do not exist yet).
- A full inbox page with history and filters (the data model allows it; the first UI is the dropdown). **(decided: dropdown)**
- Real-time push (WebSocket/SSE); the bell polls. **(call)**
- Changing the username after sign-up inside ShareWise.
- Inviting people who have no account yet, and invitation links for anyone. **(decided: removed)**
- Fuzzy search or autocomplete of usernames (see anti-goals).

**Anti-goals (a failure even if it works)**
- A search that lets someone list or browse accounts. Lookup is exact match, one account at a time.
- The notification module becoming a dependency of group or settlement (it must stay a leaf; the module graph stays one-way).
- Notification text or names stored in events or copied into other modules (events carry ids only).
- A second copy of an invitation's state. The state lives only in `group`; the bell asks `group` for it (section 5.3).

## 3. Decisions
| # | Decision | Source |
|---|---|---|
| N1 | The inbox is its own module `notification`, a leaf nobody depends on. | recommended, accepted |
| N2 | Invitations go only to existing accounts; the link-for-anyone invitation is removed. | decided |
| N3 | Lookup key: exact **username or email**. | decided |
| N4 | Usernames are owned by **Keycloak**: the realm stops using the email as username (`registrationEmailAsUsername` false); on first login `Account.username` is copied from `preferred_username` (same pattern as email and display name, D10/C2). | decided (option A) |
| N5 | Login accepts username **or** email, always with the password (`loginWithEmailAllowed` stays true). | decided |
| N6 | The inbox is a bell with a dropdown (option A of the mockup). Accept and Decline sit on the invitation. | decided |
| N7 | Entries: joined / declined (to the inviter); payment recorded (to the creditor); payment confirmed / rejected (to the payer); debt waived (to the debtor); member removed. Expenses are excluded. A received invitation is shown in the same bell but comes from `group` (N9). | decided ("do all"), refined by N9 |
| N8 | Base is `main`. The payment, waiver and removal entries are built last, after `feature/settlements` is merged into `main` (the settlement events exist only there; `MemberRemoved` and `InvitationDeclined` exist nowhere yet). | decided |
| N9 | **(review)** A received invitation is not an inbox row. `group` offers "my pending invitations" as a query; the bell merges that list with the notification entries. | decided (option 2 of the design review) |
| N10 | `MemberJoined` gains `invitedBy` (null for the creator) so the inviter can be told. | decided (O1, option A) |
| N11 | "No account found" is shown as it is; no rate limit on lookups. | decided |

### Design review (independent read-only reviewer, 03.11.26)
What it found, how we verified it, and what we did. Kept for P2 (limits and change drivers).
- **`PENDING` meant two things.** The design expired invitations at read time but kept a partial unique index on the *stored* `PENDING` status, so an expired, untouched invitation would have blocked re-inviting for ever. The sweep that fixed it was a second mutation hidden in `inviteMember`. **Now:** `EXPIRED` is never stored; the status is derived (section 5.1), and there is no partial index. The group row lock is the single guard.
- **A sentence of ours was wrong.** "Accept and decline may store `EXPIRED`" cannot happen: they throw inside `@Transactional` (no `noRollbackFor` exists in the backend), so the write rolls back. Removed.
- **An invitation is state, not an event (largest finding).** Showing it as an inbox row created a second copy that had to be kept in step: two mark-read listeners, an invitation-status lookup in a port, an ordering case, and a badge that dropped late. It also made a failed listener hide the invitation from the invitee for up to 7 days while the inviter got `ALREADY_INVITED` (our failure-mode text claimed the inviter could see it; no screen shows that). **Now:** N9.
- **Left as it is, on the reviewer's advice:** the module split and the leaf direction, `Invitation` inside `Group` (accept must add the `Membership` and mark the invitation atomically), usernames owned by Keycloak, the `@`-free `Username`, the idempotent insert.
- **Dropped as over-engineering:** the partial index, the F22 ordering test, the "no row for yourself" rule (unreachable), the realm-pattern test (F13), the dedicated F15 test.
- **Not taken:** `invitationId` on `MemberJoined` (it would remove `occurred_at` from the dedupe key but reopens N10). Taken later if the key ever hurts.
- **Change driver for P3:** every bell poll makes synchronous lookups (`UserProfiles`, `GroupDirectory`, the pending-invitation query). As separate services this becomes a chain on the hottest read path. At extraction, keep a local copy of names fed by events.

## 4. Usernames (account context)
- `Account` gets `username` (value object `Username`, in `account.domain`; it appears in a port record only as plain text, like `DisplayName`). Stored **lower case**, unique (`UNIQUE` index on the column).
- Rule for `Username` **(call)**: 3 to 30 characters of `a-z 0-9 . _ -`, no `@`. Why no `@`: the lookup tells an email from a username by the `@`. If a username could look like an email, a person could register the username `bob@example.com` and receive the invitations meant for the real Bob.
- The Keycloak realm enforces the same rule at sign-up (a validator on the username attribute in the realm's user profile), so the sign-up form rejects a bad name instead of ShareWise failing on first login (the lesson of C7). `editUsernameAllowed: false`. **(call)**
- First login: take `preferred_username` as it is. There is **no legacy handling**: the dev database and the Keycloak realm are deleted and created fresh **(decided)**. A token whose username is not a valid `Username` is refused with a clear 401 instead of being repaired.
- The sign-up form therefore asks for username, email, first name, last name, password.
- `UserProfile` gains `username` (plain text); `GET /me` returns it.
- New port method `UserProfiles.findByIdentifier(String identifier)`: `Optional<UserProfile>`, works without a transaction, matches exact and case-insensitive; an identifier with an `@` is an email, otherwise a username; an inactive account is not found.
- Migration `V106__account_username.sql`: add the column as `NOT NULL` with a `UNIQUE` index (no backfill, the database starts empty). A migration that is on `main` is never edited, so this is a new file.

## 5. Targeted invitations (group context)
### 5.1 Model
`Invitation` no longer carries a token and an optional email. It carries `invitee: UserId` (the account it is for), `createdBy`, `createdAt`, `expiresAt` (R9: 7 days). Diagram delta in section 10.

**Status is derived, `EXPIRED` is never stored (review).** `InvitationStatus` keeps the four diagram constants. The stored status is `PENDING`, `ACCEPTED` or `DECLINED`. `Invitation.statusAt(now)` returns `EXPIRED` for a stored `PENDING` whose `expiresAt` has passed. At the exact instant `expiresAt` it is still valid (`now` must be *after* it, as today). Every reader asks `statusAt(now)`: accept, decline, the `ALREADY_INVITED` check and the pending-invitation query. This is safe because every write to `invitations` goes through the `Group` root under the group row lock (document 01, 2.3).

### 5.2 Operations on the `Group` root
Each returns its event; the service stays thin: load (with the row lock), call, save, publish.
| Operation | Rule | Event |
|---|---|---|
| `inviteMember(invitee, inviter, now)` | the inviter is an active member (`NOT_A_MEMBER`); the invitee is not an active member (`ALREADY_MEMBER`); the invitee has no invitation to this group whose `statusAt(now)` is `PENDING` (`ALREADY_INVITED`, new); valid 7 days (R9) | `MemberInvited(groupId, invitationId, invitee, invitedBy, occurredAt)` |
| `acceptInvitation(invitationId, user, now)` | the invitation is for `user` (otherwise `INVITATION_NOT_FOUND`, so nobody can probe other people's invitations), stored `PENDING` (else `INVITATION_NOT_FOUND`), not expired (`INVITATION_EXPIRED`), user not yet a member | `MemberJoined(groupId, userId, role, invitedBy, occurredAt)` |
| `declineInvitation(invitationId, user, now)` (new) | same checks as accept; sets `DECLINED`; nothing joins | `InvitationDeclined(groupId, invitationId, invitee, invitedBy, occurredAt)` (new) |

- `GroupService.inviteMember(groupId, inviter, identifier)` resolves the identifier through `UserProfiles.findByIdentifier` (group already depends on `account::api`) and passes the `UserId` to the domain. No account found: `ACCOUNT_NOT_FOUND` (404, new); the domain never calls another module. *(Changed after the build, commit `a913f47`: the inviter's membership (`NOT_A_MEMBER`, first row above) is no longer checked inside `Group.inviteMember`. The service checks it once, as its own step (`Group.requireActiveMember`) before the account lookup, so a non-member learns nothing about which accounts exist. Trade-off: `Group.inviteMember` alone no longer refuses a non-member inviter, so any other caller must call `requireActiveMember` first; today `GroupService` is the only caller. It removes the double check; pinned by `GroupServiceTest.aNonMemberCannotInviteAndLearnsNothingAboutAccounts` and `GroupTest.requireActiveMember*`.)*
- **The group row lock is what keeps one open invitation per person and group.** `inviteMember` loads the group with `findByIdForUpdate`, the lock accept already uses (a one-word difference), so two members inviting the same person together are serialized and the second gets `ALREADY_INVITED`. There is no database index for it and no thread-based test; the rule is tested sequentially. (An invite cannot race with the invitee's own accept: while an invitation is open a new invite is refused, and after accepting the person is a member.)
- Accept and decline on an expired invitation throw `INVITATION_EXPIRED` (410) and write nothing.
- Re-inviting after `DECLINED` or expiry is allowed. **(call)**
- **Event shapes:** `MemberInvited` replaces `email` with `invitee: UserId`; it has no consumer yet (a later email channel would be one), like `AllBalancesSettled`. `MemberJoined` gains `invitedBy: UserId`, the member who sent the invitation, `null` when the person created the group; its Javadoc says so. **(N10)** Rejected: a second event `InvitationAccepted`.
- Migration `V205__invitations_for_accounts.sql`: add `invitee_id` as `NOT NULL` (the database starts empty); drop the `token` and `email` columns; add a plain index on `invitee_id` for the pending-invitation query. No partial unique index.

### 5.3 "My pending invitations" (review, N9)
- `GET /invitations` answers the caller's invitations whose `statusAt(now)` is `PENDING`, newest first: `invitationId`, `group {id, name}`, `invitedBy {userId, displayName, username}`, `expiresAt`. Only the caller's own; nothing else is filterable.
- Implemented in `group`: a repository query on `invitee_id`, `status = PENDING` and `expiresAt >= now` (the same boundary as `statusAt`), run inside a read-only transaction; the inviter's name comes from `UserProfiles`. It returns plain records, never entities. No port for other modules is needed: only the frontend consumes it.
- Accept and decline stay `POST /invitations/{invitationId}/accept` and `.../decline`.

## 6. The `notification` module
### 6.1 Position and dependencies
New bounded context `Notification` (generic subdomain, like Account), layout as every module. Allowed dependencies: `account::api, group::api, shared`; later `settlement::api`. **Nobody depends on it.** This is a design decision (new module, new dependencies); the line goes into `package-info.java` and `docs/conventions/01`, and `ModularityTests` checks it.

Why a module and not a table in `group`: payment and waiver entries come from `settlement`, and `settlement` already depends on `group`. Notification rows inside `group` would force `group → settlement`, the cycle `ModularityTests` refuses (document 01, 5.1).

### 6.2 Model
- Aggregate root `Notification`: `id: NotificationId`, `recipient: UserId`, `type: NotificationType`, `groupId: GroupId`, `actor: UserId` (who did it), `reference: UUID` (what it points at: the invitation, the payment, or the person who joined), `occurredAt`, `readAt` (null = unread).
- Operations: `Notification.notify(...)` (factory) and `markRead(now)` (idempotent: reading twice keeps the first time).
- `NotificationType` starts with `MEMBER_JOINED`, `INVITATION_DECLINED`; later `PAYMENT_TO_CONFIRM`, `PAYMENT_CONFIRMED`, `PAYMENT_REJECTED`, `DEBT_WAIVED`, `MEMBER_REMOVED`.
- A notification is **immutable except `readAt`**. It stores ids, never texts or names; the texts are built from the type and the looked-up names when the inbox is read (names through `UserProfiles`, group names through `GroupDirectory`, see 6.4). Why: names change, texts can be reworded or translated, and events must stay ids only (document 01).
- Table `notifications`, migration `V501__notifications.sql` (new range `V5xx`), `UNIQUE (recipient_id, type, reference, occurred_at)` so a redelivered event cannot create a second row (idempotent consumer). `occurred_at` is in the key because `MEMBER_JOINED` has no invitation to point at: its `reference` is the id of the person who joined, and without the time a person who leaves and rejoins would be dropped as a duplicate. A redelivered event carries the same `occurredAt`, so it still collides. The insert uses `ON CONFLICT DO NOTHING`, the same technique as `AccountRepository.insertIfAbsent`. Index on `(recipient_id, read_at, occurred_at DESC)` for the bell.

### 6.3 Which event becomes which entry
| Event (publisher) | Recipient | Type | Built |
|---|---|---|---|
| `MemberJoined` with `invitedBy` set (group) | the inviter | `MEMBER_JOINED` | on `main` |
| `InvitationDeclined` (group, new) | the inviter | `INVITATION_DECLINED` | on `main` |
| `SettlementPaymentRecorded` (settlement) | `toCreditor` | `PAYMENT_TO_CONFIRM` | after settlements is merged |
| `SettlementPaymentConfirmed` / `Rejected` (settlement) | the payer | `PAYMENT_CONFIRMED` / `PAYMENT_REJECTED` | after settlements is merged |
| `DebtWaived` (settlement) | the debtor | `DEBT_WAIVED` | after settlements is merged |
| `MemberRemoved` (group) | the removed person | `MEMBER_REMOVED` | when `removeMember` exists |

`MemberInvited` is deliberately not in this table: the received invitation comes from `group` (5.3). `MemberJoined` with `invitedBy == null` (the creator) creates nothing.

One `<Event>Listener` per event, `@ApplicationModuleListener` (asynchronous, after commit, kept in the event publication registry). Listeners take every id from the event and never use request-scoped state (document 02, pitfall 3). Adding a type later is one listener, one enum constant, one text in the frontend.

### 6.4 Ports and endpoints
- Group `api` gets one read-only port `GroupDirectory` **(call)** with one method, the name for a set of group ids. It answers without a transaction; unknown ids are left out. It needs an integration test without a transaction (document 02, pitfall 5).
- **When an entry counts as read** **(call)**: when the user clicks it or uses "mark all as read". Opening the dropdown alone marks nothing. (A pending invitation has no read state: it is shown until answered or expired, and counts in the badge until then.)
- **Retention** **(call)**: entries are never deleted in this version (like completed `event_publication` rows, document `2026-10-27`); a cleanup is parked.
- HTTP (contract first, `/contract-change`); every call is for the caller's own data only:
  - `GET /notifications?unread=true&limit=10` returns entries with `id`, `type`, `occurredAt`, `readAt`, `actor {userId, displayName, username}`, `group {id, name}`, `reference`; plus `unreadCount`. `limit` is 1 to 50, default 10.
  - `POST /notifications/{id}/read`, `POST /notifications/read-all`. Another person's (or an unknown) id answers 404 `NOTIFICATION_NOT_FOUND` (new code, added in the plan).
  - Group: `POST /groups/{groupId}/invitations` with `{ "identifier": "alice" }` (replaces the link creation), `GET /invitations` (5.3), `POST /invitations/{invitationId}/accept` (replaces the token path), `POST /invitations/{invitationId}/decline`.
  - New error codes `ACCOUNT_NOT_FOUND` (404), `ALREADY_INVITED` (409), `NOTIFICATION_NOT_FOUND` (404), in all three places (document 03, section 3).

### 6.5 Seam for email later
Delivery channels are separate from the inbox: a later `EmailNotifier` is another listener on the same events (`MemberInvited` among them), reading the recipient's email through `UserProfiles`. Not built now.

## 7. Frontend
- **One route handler merges the two sources** (`/api/inbox`, a Next.js server route): it calls `GET /invitations` and `GET /notifications` and returns both. If one call fails the other part is still shown, with a small error line. **(call)**
- Header: bell with the badge = pending invitations + unread entries; polled every 30 s and when the tab regains focus **(call)**; a short delay after an event is expected (eventual consistency, like balances). The pending-invitation part is not delayed: it is a direct query.
- Dropdown: pending invitations on top ("**Alice** invited you to **Mallorca**", Accept, Decline), then the latest 10 entries. Accept calls accept, then goes to `/groups/{id}`; Decline removes the row. A 404 right after a successful accept is treated as "already done" and the button is disabled while the call runs.
- Group page: the invite box takes username or email; results: sent / no account found ("The person must have signed in to ShareWise once") / already a member / already invited.
- Removed: the `/join/[token]` page, `JoinGroup`, the link `InviteButton` and the matching route handlers and e2e step; replaced by the invite box and the bell.
- Texts per type live in one table, new error codes in `frontend/src/lib/errors.ts`. Names are rendered as React text, never as HTML.

## 8. Build order (one concern per commit)
1. `contract`: invitations (create, list, accept, decline), notifications, username, error codes (own commit).
2. Account: Keycloak realm, `Username`, migration, `UserProfiles.findByIdentifier`, `GET /me`, test helper `KeycloakUsers.create`.
3. Group: derived status, targeted `inviteMember` with the lock, `declineInvitation`, `InvitationDeclined`, `MemberJoined.invitedBy`, `GET /invitations`, `GroupDirectory`, migration, controller.
4. Notification module: model, migration, listeners for `MemberJoined` and `InvitationDeclined`, endpoints, `ModularityTests` line.
5. Frontend: invite box, `/api/inbox`, bell and dropdown, removal of the link flow, e2e.
6. After `feature/settlements` is merged into `main`: listeners for payment, confirmation, rejection and waiver; `MemberRemoved` when `removeMember` exists.

Plan checklist items for other work (parked failure modes F29, F30): whoever builds `closeForSettlement`/`archiveGroup` makes invite and accept answer `GROUP_NOT_OPEN`; whoever builds `leaveGroup`/`removeMember` decides how a former member rejoins (the `uq_memberships_group_user` constraint would reject a second row).

## 9. Failure modes (edge-case-probe plan mode, updated after the design review)
Marks: **design** = handled by a rule in this document; **test** = a test from the start; **parked** = not in this version, with why and the trigger. Scope rule: only cases that can happen in ordinary use or have a security or data impact; no thread-based tests.

### Authorization and input
| # | Case | Handling |
|---|---|---|
| F5 | Accept or decline an invitation meant for somebody else, or a random id. | **design**: `INVITATION_NOT_FOUND` (404) for both. **test** at API level, with an invitee who is also a member of another group. |
| F6 | List or mark read the notifications, or list the pending invitations, of another person. | **design**: every query filters on the caller; a foreign notification id answers 404. **test**: Alice cannot read or mark Bob's entry; `read-all` touches only her rows; Bob's invitations never appear for Alice. |
| F7 | A non-member, or a former member, invites. | **design**: `NOT_A_MEMBER` (existing rule). **test**. |
| F8 | Identifier blank, whitespace only, 255+ characters, upper case, unicode look-alikes, `%` or `_` (wildcards if someone writes a `LIKE`), `@` only, two `@`. | **design**: trim, lower case, exact equality only (never `LIKE`), blank is 400, longer than 254 is 400. Usernames are ASCII only. **test** (parameterized) incl. `%` and `_`. |
| F9 | Invite yourself; invite a current member; invite somebody with an open invitation. | **design**: `ALREADY_MEMBER` / `ALREADY_INVITED`. **test**, sequential. |
| F10 | `limit` of 0, negative or huge on `GET /notifications`; `unread=maybe`. | **design**: limit 1 to 50, else 400. **test**. |
| F11 | Every new endpoint without a token, with an expired token. | **design**: 401 `UNAUTHENTICATED` like the others. **test** one per endpoint group. |

### Accounts and usernames
| # | Case | Handling |
|---|---|---|
| F12 | A person signed up at Keycloak but has never opened ShareWise: no `Account` exists yet (created on the first request), so the lookup says "No account found". | **design, accepted**: sign-up lands on ShareWise, which calls `GET /me` at once, so the window is short. The message names it (section 7). **test**: a Keycloak user without `GET /me` is not found; after `GET /me` it is. |
| F14 | A username with `@` or upper case reaches ShareWise (admin console, direct API). | **design**: `Username` normalizes to lower case and rejects `@`; first login is refused with 401. Prevents the hijack of invitations meant for an email address. **test** both. |
| F15 | A username clash inside `insertIfAbsent` (`ON CONFLICT DO NOTHING` swallows *any* unique conflict, which the code reads as "lost the race"). Not only a Keycloak bug: an admin deletes Keycloak user `bob`, a new person registers `bob`, and the old account row still holds the name, so the new person gets a 401 for good. | **design, accepted** (the email case already behaves this way). Fix the warning text so it names the username as well as the email. No dedicated test. |
| F16 | Email changed in Keycloak after the account was created: ShareWise keeps the first one (D10). | **parked**: an accepted rule of this project. |
| F17 | Invitee deactivated after being invited. | **design**: an inactive account is not found by new lookups; an existing invitation stays, and accept fails with 401 because the token is refused. **test**: lookup of an inactive account. |
| F18 | The realm no longer uses the email as username, so `KeycloakUsers.create` must supply a unique username per test (shared Keycloak and database). | **design**: the helper generates a unique name; fixtures expose it. |

### Events and eventual consistency
| # | Case | Handling |
|---|---|---|
| F19 | The same event twice. | **design**: unique key plus `ON CONFLICT DO NOTHING`. **test**: publish twice, one row. |
| F4 | A person joins, leaves and joins again: the second `MEMBER_JOINED` must not look like a duplicate. | **design**: `occurred_at` is part of the key (6.2). **test**: same event twice gives one row; two joins at different times give two. |
| F20 | `MemberJoined` for the group creator (`invitedBy` is null). | **design**: no row. **test**. |
| F21 | A listener fails. | **parked, consequence accepted**: redelivery only on restart (document `2026-10-27`). The loss is one "Bob joined" or "Bob declined" entry; **an invitation can never be hidden** because it is a query, not an event. Revisit at the broker step (Ex9). |
| F23 | Code runs outside a request or a transaction: listeners using `CurrentUser`, a port reading a lazy collection (pitfalls 1 and 3). | **design**: listeners take ids from the event only. **test**: integration tests for `GroupDirectory` and `UserProfiles.findByIdentifier` called with no transaction (pitfall 5). |
| F24 | A user or group cannot be resolved when the inbox is read (account gone, group missing). | **design**: ports leave unknown ids out; the response uses `actor` null / `group` null and the frontend shows "someone" / "a group". **test**. |
| F25 | Old rows in `event_publication` carry the old `MemberInvited` / `MemberJoined` shape. | **design**: the database is deleted and recreated (decided), so none exist. |

### Lifecycle
| # | Case | Handling |
|---|---|---|
| F1 | Re-invite after an invitation expired; accept or decline exactly at, and after, `expiresAt`. | **design**: derived status, no stored `EXPIRED`, no index (5.1). **test** (domain unit): re-invite after 7 days works; at exactly `expiresAt` the old one is still valid and a new invite is `ALREADY_INVITED`; one second later the old one is `EXPIRED` and accept answers 410. |
| F2 | Two members invite the same person at the same instant. | **design**: the group row lock serializes them (5.2); the second gets `ALREADY_INVITED`. No thread test; F9 covers the rule. |
| F26 | Double accept (double click, two tabs). | **design**: the second one sees `ACCEPTED` and answers `INVITATION_NOT_FOUND` (404). The frontend disables the button while the call runs and treats a 404 after a success as done. **test**: accept twice in a row gives 200, then 404, one membership. |
| F27 | Accept and decline at the same moment. | **design**: the group lock serializes them; the second answers 404. No extra test (same status check as F26). |
| F28 | Decline an expired invitation. | **design**: 410 `INVITATION_EXPIRED`, same checks as accept. **test**. |
| F29 | Group closed or archived while an invitation is open. | **parked, trigger `closeForSettlement`/`archiveGroup`** (section 8). Cannot happen on `main` today. |
| F30 | A former member is invited again and accepts: `uq_memberships_group_user` rejects the second row (500). | **parked, trigger `leaveGroup`/`removeMember`** (section 8, R10, `docs/notes/2026-10-28-membership-copy-decision.md`). Unreachable on `main` today. |

### Abuse and display
| # | Case | Handling |
|---|---|---|
| F31 | Invitation spam: many groups inviting the same person, or re-inviting after every decline; the pending list grows. | **parked**: no limit in this version. Trigger: a real complaint; the cheap fix is a cool-down per (group, invitee) after a decline. |
| F32 | A group name or display name such as `<script>` or a very long name appears in the bell. | **design**: React text only, names already have length limits. **test**: a component test renders `<img onerror>` as text, in an invitation and in an entry. |
| F33 | The bell polls with an expired token or while signed out; one of the two calls behind `/api/inbox` fails. | **design**: the poll stops on 401 as the pages do today; a partial failure shows the part that loaded. **test** (component). |
| F34 | Account enumeration by "No account found". | **accepted (decided N11)**: only a signed-in group member can ask; no rate limit. |
| F35 | An entry about a group the person was removed from links to a page they cannot open. | **design**: `MEMBER_REMOVED` shows no link; the group page already answers 403/404. |

### Ranked for scope (impact × likelihood)
1. **F1, F26, F9** (re-invite after expiry, double accept, duplicate invite): ordinary use, mandatory tests.
2. **F5, F6, F8, F14** (authorization and lookup input): mandatory tests; F14 is the one with a security impact.
3. **F19, F4, F20** (inbox duplicates, rejoin, creator): mandatory tests.
4. **F12, F18** (sign-up and test set-up): the message wording and the helper; F12 is the one a user will actually hit.
5. **Parked with a trigger:** F29, F30. **Parked with a reason:** F31, F16, F21.

## 10. Documents that change with the code
- **Diagram** (`docs/domain/ShareWise_Domain_Model.drawio`) and `docs/diagram-deltas.md`: new context Notification (`Notification`, `NotificationType`); `Account.username`; `Invitation.invitee: UserId` replacing the email, and `EXPIRED` derived rather than stored; event `MemberInvited` carries `invitee`; `MemberJoined.invitedBy`; read model "pending invitation". A code name the diagram lacks is a defect, so the diagram changes in the same commit as the code.
- `docs/conventions/01` (module table, dependencies, vocabulary, "words we do not use"), `02` if a new test pattern appears, `docs/api/openapi.yaml`, `RUNNING.md` (realm change, new sign-up form), `docs/2026-10-30-keycloak-auth*.md` (username claim), the e2e flow, `docs/devsetup/CATALOG.md` (what the design review found that we would have missed).
- Grep for the old terms: `token`, `createInvitation`, `link invitation`, `/join/`, `INVITATION_RECEIVED`.

## 11. Open decisions for the user
None. Everything is decided or marked (call).
