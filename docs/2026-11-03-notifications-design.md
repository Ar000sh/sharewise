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
- **The partial unique index on `(group_id, invitee_id) WHERE status = 'PENDING'` is what guarantees one pending invitation per person and group.** `inviteMember` loads the group with the same row lock that accept uses (`findByIdForUpdate`, a one-word difference), so two members inviting the same person together get a clean `ALREADY_INVITED` for the second instead of a database error. There is no dedicated parallel test: the rule is tested sequentially. (An invite cannot race with the invitee's own accept: while an invitation is pending a new invite is refused, and after accepting the person is a member.)
- **Expiry is checked at read time, and stored when touched.** An invitation counts as expired when `now` is after `expiresAt` (as accept does today); at the exact instant it is still valid. Because the partial unique index looks at the *stored* status, an expired invitation still stored as `PENDING` would block every re-invitation forever. So `inviteMember` first sets the stored status of that invitee's expired `PENDING` invitations in this group to `EXPIRED` (a `Group` root method, same transaction), and only then checks for `ALREADY_INVITED`. Accept and decline on an expired invitation answer `INVITATION_EXPIRED` (410) and may store `EXPIRED` too. **(found by the probe, section 9 F1)**
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
- Table `notifications`, migration `V501__notifications.sql` (new range `V5xx`), `UNIQUE (recipient_id, type, reference, occurred_at)` so a redelivered event cannot create a second row (idempotent consumer). `occurred_at` is in the key because `MEMBER_JOINED` has no invitation to point at: its `reference` is the id of the person who joined, and without the time a person who leaves and rejoins would be dropped as a duplicate. A redelivered event carries the same `occurredAt`, so it still collides; the insert uses `ON CONFLICT DO NOTHING`, the same technique as `AccountRepository.insertIfAbsent`. Index on `(recipient_id, read_at, occurred_at DESC)` for the bell.
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
- **When an entry counts as read** **(call)**: when the user clicks it, answers an invitation, or uses "mark all as read". Merely opening the dropdown does not mark anything, so an unanswered invitation keeps the badge until it is dealt with. **Answering is detected by a listener, not by the browser or by `group`:** `group` must not call `notification` (it is a leaf), and the browser making a second call would leave the badge wrong when the invitation is answered in another tab. So `notification` also listens to `MemberJoined` and `InvitationDeclined` and marks the invitee's `INVITATION_RECEIVED` entry of that group as read (one pending invitation per person and group is guaranteed, section 5). The badge therefore drops a moment after the answer (eventual consistency). **(found by the probe, F6)**
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

## 9. Failure modes (edge-case-probe, plan mode, 03.11.26)
Marks: **design** = handled by a rule in this document; **test** = goes into the plan as a test from the start; **parked** = not in this version, with why and the trigger that brings it back. Ranked by impact × likelihood inside each block. "F" numbers are referenced from the sections above.

### Fixed in the design by this probe (were flaws)
| # | Flaw found | Fix |
|---|---|---|
| F1 | Expiry was only checked at read time, but the unique index looks at the stored `PENDING` status: an expired, never-touched invitation would block re-inviting that person forever. | `inviteMember` stores `EXPIRED` for the invitee's expired invitations first (section 5). **test**: invite, advance the clock past 7 days, invite again succeeds; at exactly `expiresAt` the old one is still valid. |
| F2 | Two members inviting the same person in the same instant both pass the in-memory check; the second would fail on the unique index with a 500. (Very unlikely in a friends-sized group; an invite racing with the invitee's own accept is impossible, see section 5.) | `inviteMember` takes the same row lock as accept, one word (section 5). **test**: sequential only, a second invite of the same person gives `ALREADY_INVITED`. No thread-based test. |
| F3 | "Answering an invitation marks its entry read" implied `group` calls `notification` or the browser makes a second call. | A listener in `notification` on `MemberJoined` / `InvitationDeclined` (section 6.4). **test**: after accept the entry is read; accepting in another session also clears the badge. |
| F4 | The unique key `(recipient, type, reference)` would drop a rejoin as a duplicate (`MEMBER_JOINED` has no invitation id). | `occurred_at` joins the key (section 6.2). **test**: same event twice gives one row; same person joining twice gives two. |

### Authorization and input
| # | Case | Handling |
|---|---|---|
| F5 | Accept or decline an invitation meant for somebody else, or a random id. | **design**: `INVITATION_NOT_FOUND` (404) for both, so ids cannot be probed. **test** at API level, with an invitee who is also a member of another group. |
| F6 | Mark read / list the notifications of another person. | **design**: every query filters on the caller; a foreign id answers 404. **test**: Alice cannot read or mark Bob's entry, `read-all` touches only her rows. |
| F7 | A non-member, or a former member, invites. | **design**: `NOT_A_MEMBER` (existing rule). **test**. |
| F8 | Identifier blank, whitespace only, 255+ characters, upper case, unicode look-alikes, `%` or `_` (wildcards if someone writes a `LIKE`), `@` only, two `@`. | **design**: trim, lower case, exact equality only (never `LIKE`), blank is 400, longer than 254 is 400. Usernames are ASCII only, so look-alike letters never match. **test** (parameterized) incl. `%` and `_`. |
| F9 | Invite yourself; invite a current member; invite somebody with a pending invitation. | **design**: `ALREADY_MEMBER` / `ALREADY_INVITED`. **test**. |
| F10 | `limit` of 0, negative or huge on `GET /notifications`; `unread=maybe`. | **design**: limit 1 to 50 (default 10), else 400. **test**. |
| F11 | Every new endpoint without a token, with an expired token. | **design**: 401 `UNAUTHENTICATED` like the others. **test** one per endpoint group. |

### Accounts and usernames
| # | Case | Handling |
|---|---|---|
| F12 | A person signed up at Keycloak but has never opened ShareWise: no `Account` exists yet (accounts are created on the first request), so the lookup says "No account found". | **design, accepted**: the sign-up flow lands on ShareWise, which calls `GET /me` at once, so this is a short window. The message names it: "No account found. The person must have signed in to ShareWise once." **test**: a Keycloak user without `GET /me` is not found; after `GET /me` it is. |
| F13 | The sign-up form's username rule (Keycloak validator) and `Username` in Java drift apart. | **test**: reads the pattern from `sharewise-realm.json` and checks it against the same sample list as `Username`. |
| F14 | A username with `@` or upper case reaches ShareWise (admin console, direct API). | **design**: `Username` normalizes to lower case and rejects `@`; first login is refused with 401 (section 4). Prevents the hijack of invitations meant for an email address. **test** both. |
| F15 | A username unique violation inside `insertIfAbsent`. It uses `ON CONFLICT DO NOTHING`, which swallows *any* unique conflict, so the code reads it as "lost the race" and may answer a confusing 401. | **test**: two accounts with the same username (only possible through a Keycloak bug) end in a logged warning and a 401, never a half-created account. Extend the warning text that names the email so it names the username too. |
| F16 | Email changed in Keycloak after the account was created: ShareWise keeps the first one (D10), so inviting by the new email says "not found", and the old email still finds the person. | **parked**: same as D10, an accepted rule of this project; revisit with an account-sync story. |
| F17 | Invitee deactivated or removed after being invited. | **design**: an inactive account is not found by new lookups; an existing invitation stays, and accept fails with 401 because the token is refused. **test**: lookup of an inactive account. |
| F18 | Test suite: realm now has no email-as-username, so `KeycloakUsers.create` must supply a unique username per test (shared Keycloak and database). | **design**: the helper generates `u` + unique suffix; fixtures expose it. |

### Events and eventual consistency
| # | Case | Handling |
|---|---|---|
| F19 | The same event twice. | **design**: unique key plus `ON CONFLICT DO NOTHING`. **test**: publish twice, one row. |
| F20 | `MemberJoined` arrives for the group creator (`invitedBy` is null), or `actor` equals the recipient. | **design**: no row. **test**. |
| F21 | A listener fails: the invitation exists, the inbox row does not. | **parked, consequence accepted**: redelivery only on restart (document `2026-10-27`). The inviter still sees the pending invitation and can re-invite after 7 days. Trigger to revisit: the broker step (Ex9). |
| F22 | `InvitationDeclined` or `MemberJoined` is processed before `MemberInvited` (no ordering across events). | **design**: rows are independent. If the "mark read" runs first, there is nothing to mark and the later `INVITATION_RECEIVED` row stays unread although answered. **test**: that order, and the inbox then shows the invitation's real status (not the buttons) because the status is looked up at read time (section 6.4). The unread badge for it is a known small inconsistency; **parked**. |
| F23 | Listener code calls `CurrentUser` / request-scoped state (pitfall 3) or reads a lazy collection without a transaction (pitfall 1). | **design**: listeners take ids from the event only. **test**: integration tests for `GroupDirectory` and `UserProfiles.findByIdentifier`, called with no transaction (pitfall 5). |
| F24 | A user or group cannot be resolved when the inbox is read (account gone, group missing): ports leave unknown ids out. | **design**: the response uses a neutral fallback (`actor` null, `group` null) and the frontend shows "someone" / "a group". **test**. |
| F25 | Old rows in `event_publication` carry the old `MemberInvited` shape (with `email`). | **design**: the database is deleted and recreated (decided), so none exist. |

### Lifecycle and concurrency
| # | Case | Handling |
|---|---|---|
| F26 | Double accept (double click, two tabs). | **design**: the second one sees `ACCEPTED`, answers `INVITATION_NOT_FOUND` (404; today's link flow would answer `ALREADY_MEMBER`). The frontend disables the button while the call runs and treats the 404 after a success as "already done". **test**: accept twice in a row gives 200, then 404, one membership (sequential, no threads). |
| F27 | Accept and decline at the same moment. | **design**: the existing group lock in accept and decline serializes them; the second answers 404. **no extra test**: the status check is the same one F26 tests. |
| F28 | Decline an expired invitation. | **design**: 410 `INVITATION_EXPIRED`, same checks as accept. **test**. |
| F29 | Group closed or archived while an invitation is open. | **parked, trigger `closeForSettlement`/`archiveGroup`**: accept and invite must then answer `GROUP_NOT_OPEN`; the status check belongs in `Group`. Cannot happen on `main` today. The plan carries a checklist item for whoever builds those operations. |
| F30 | A former member is invited again and accepts: `uq_memberships_group_user` would reject the second `Membership` row (500). | **parked, trigger `leaveGroup`/`removeMember`**: those operations must define whether a former member's row is reactivated (R10, `2026-10-28-membership-copy-decision.md`). Unreachable on `main` today; the plan carries a checklist item. |

### Abuse and display
| # | Case | Handling |
|---|---|---|
| F31 | Invitation spam: one user creates many groups and invites the same person, or re-invites after every decline. | **parked**: no limit in this version (decided: no rate limit). Trigger: any real user complaint; the cheap fix is a cool-down per (group, invitee) after a decline. |
| F32 | A group name or display name such as `<script>` or an extremely long name appears in the inbox. | **design**: texts are built in the frontend as React text, never as HTML; names already have length limits. **test**: a component test renders `<img onerror>` as text. |
| F33 | The bell polls with an expired token or while signed out. | **design**: the poll stops on 401 and the page handles sign-in as it does today. **test** (component). |
| F34 | Account enumeration by "No account found". | **design, accepted (decided)**: only a signed-in group member can ask; no rate limit. |
| F35 | An inbox entry about a group the person left or was removed from links to a page they cannot open. | **design**: `MEMBER_REMOVED` shows no link; the group page already answers 403/404 and the frontend shows its normal error. |

### Ranked for scope (impact × likelihood)
1. **F1, F26** (re-invite blocked forever after expiry, double accept): fixed in the design, mandatory tests; these happen in ordinary use and would be real bugs in a demo. F2 and F27 are one-word or no-cost, covered by the sequential tests; no concurrency tests.
2. **F3, F19, F4** (inbox consistency: read marking, duplicates): fixed in the design, mandatory tests.
3. **F5, F6, F8, F14** (authorization and lookup input): mandatory tests; F14 is the only one with a security impact.
4. **F12, F13, F18** (sign-up and realm): tests; F12 is the one the user will actually hit, so its message is worded for it.
5. **F29, F30** are parked with a trigger and a checklist item in the plan; **F31, F16, F21, F22** are parked with a reason above.

## 10. Documents that change with the code
- **Diagram** (`docs/domain/ShareWise_Domain_Model.drawio`) and `docs/diagram-deltas.md`: new context Notification (`Notification`, `NotificationType`); `Account.username`; `Invitation.invitee: UserId` replacing the email; event `MemberInvited` carries `invitee`; `MemberJoined.invitedBy`. A code name the diagram lacks is a defect, so the diagram changes in the same commit as the code.
- `docs/conventions/01` (module table, dependencies, vocabulary, "words we do not use"), `02` if a new test pattern appears, `docs/api/openapi.yaml`, `RUNNING.md` (realm change, new sign-up form), `docs/2026-10-30-keycloak-auth*.md` (username claim), the e2e flow.
- Grep for the old terms: `token`, `createInvitation`, `link invitation`, `/join/`.

## 11. Open decisions for the user
None. O1 is decided (section 5). Everything else is decided or marked (call).
