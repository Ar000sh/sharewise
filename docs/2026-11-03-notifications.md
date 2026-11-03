# Usernames, Targeted Invitations and the Notification Inbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A person invites an existing account by exact username or email; the invitee sees the invitation in a bell with an unread count and accepts or declines it there; the inviter gets an inbox entry for the answer.

**Architecture:** Usernames live in `account` (Keycloak owns them, ShareWise copies them on first login). Invitations stay inside the `Group` aggregate, now bound to an invitee `UserId`, with a derived (never stored) `EXPIRED` status; "my pending invitations" is a query in `group`. A new leaf module `notification` consumes `MemberJoined` and `InvitationDeclined` into an idempotent inbox table. One Next.js route handler merges the two sources for the bell.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith 2.1, JPA + Flyway, Postgres 17, Keycloak (Testcontainers); Next.js 16, React 19, Vitest, Playwright, openapi-typescript.

**Spec:** `docs/2026-11-03-notifications-design.md` (read it with this plan; section numbers below refer to it). Branch: `feature/notifications`, cut from `main`.

**Paths:** relative to the repo root. `<pkg>` = `backend/src/main/java/de/hsh/favs/sharewise`, `<test>` = `backend/src/test/java/de/hsh/favs/sharewise`, `<res>` = `backend/src/main/resources`. Backend commands run in `backend/` with `./mvnw` (Docker Desktop must run); frontend commands in `frontend/`.

## Global Constraints

- Invitations are valid 7 days (R9); `now` after `expiresAt` means expired, at the exact instant it is still valid. `EXPIRED` is never stored; stored statuses are `PENDING`, `ACCEPTED`, `DECLINED`. No partial unique index.
- `Username`: trimmed, stored lower case, 3 to 30 characters of `a-z 0-9 . _ -`, never an `@`. The Keycloak realm enforces the same rule at sign-up.
- Lookup is exact equality on lower-cased, trimmed input, never `LIKE`; an identifier containing `@` is an email, otherwise a username; blank or longer than 254 characters is 400 `BAD_REQUEST`.
- New error codes: `ACCOUNT_NOT_FOUND` 404, `ALREADY_INVITED` 409, `NOTIFICATION_NOT_FOUND` 404 (a plan call: the spec says "another person's id answers 404" but names no code). Each goes into `openapi.yaml`, `ErrorCode` and `frontend/src/lib/errors.ts` in the same change (document 03, section 3).
- Event shapes: `MemberInvited(GroupId groupId, InvitationId invitationId, UserId invitee, UserId invitedBy, Instant occurredAt)`; `MemberJoined(GroupId groupId, UserId userId, GroupRole role, UserId invitedBy, Instant occurredAt)` with `invitedBy` null for the group creator; `InvitationDeclined(GroupId groupId, InvitationId invitationId, UserId invitee, UserId invitedBy, Instant occurredAt)`. Events carry ids only.
- `notification` depends on `account::api, group::api, shared` and nothing may depend on it. `group` already depends on `account::api`.
- Migrations: `account/V106__account_username.sql`, `group/V205__invitations_for_accounts.sql`, `notification/V501__notifications.sql`; each starts with a `--` comment saying why; the databases are recreated, so no backfill.
- Notification dedupe key `UNIQUE (recipient_id, type, reference, occurred_at)`, insert with `ON CONFLICT DO NOTHING`. `GET /notifications`: `limit` 1 to 50, default 10, else 400.
- The bell polls every 30 s and when the tab regains focus. No thread-based tests; concurrency rules are tested sequentially.
- Test style (document 02): sentence names, `// given // when // then`, fixtures, `@ModuleIntegrationTest`, Awaitility for events, `MutableClock` for time, vocabulary equals production (`invitee`, `invitedBy`).
- Contract first and alone in its commit (`contract(group): ...`); regenerated `schema.d.ts` goes with the frontend task. Commit format `<type>(<scope>): <subject>` with the trailer `Co-Authored-By: Claude <model> <noreply@anthropic.com>` naming the model that did the work. Never `--no-verify`. Docs change in the same commit as the code they describe.

## Review Focus

1. Identifier `%`, `_`, whitespace only, 255 characters, upper case, `bob@example.com` typed as a username → no wildcard match, 400 or "not found", never a 500 (Task 3, controller test).
2. A person who signed up at Keycloak but never called `GET /me` → "not found"; after `GET /me` found (Task 2).
3. Re-invite after 7 days, and accept or decline exactly at and one second after `expiresAt` (Task 3, domain test).
4. Double accept, accept of somebody else's invitation, decline of an expired one (Task 3, controller test).
5. A group name like `<img src=x onerror=alert(1)>` in the bell renders as text (Task 5, component test).

## Task order

```
1 contract → 2 account usernames → 3 group invitations → 4 notification module → 5 frontend → 6 closing sweep
```
Each task ends green (`./mvnw -q verify` for backend tasks, the frontend checks for Task 5) and is one or two commits. Payment, waiver and removal entries are **not** in this plan: they wait for `feature/settlements` to be merged into `main` (decision N8) and get their own small plan.

---

### Task 1: The contract

**Why:** the contract decides the HTTP interface and changes first, alone (root CLAUDE.md, source of truth 2). Use `/contract-change` for the procedure; this task is only its first commit.

**Files:**
- Modify: `docs/api/openapi.yaml`

**Interfaces:**
- Produces (names every later task uses):
  - `POST /groups/{groupId}/invitations`, body `InviteMemberRequest { identifier: string (minLength 1, maxLength 254) }`, 201 `Invitation { invitationId: uuid, invitee: UserSummary, expiresAt: date-time }`; errors 400, 401, 403 `NOT_A_MEMBER`, 404 `GROUP_NOT_FOUND` / `ACCOUNT_NOT_FOUND`, 409 `ALREADY_MEMBER` / `ALREADY_INVITED`.
  - `GET /invitations` → 200 `PendingInvitation[]` (`{ invitationId, group: GroupRef, invitedBy: UserSummary, expiresAt }`), newest first; 401.
  - `POST /invitations/{invitationId}/accept` → 200 `Group`; errors 401, 404 `INVITATION_NOT_FOUND`, 409 `ALREADY_MEMBER`, 410 `INVITATION_EXPIRED`. The token path is removed.
  - `POST /invitations/{invitationId}/decline` → 204; errors 401, 404, 410.
  - `GET /notifications?unread=<bool, default false>&limit=<1..50, default 10>` → 200 `NotificationList { unreadCount: integer, items: Notification[] }`; `Notification { id, type: MEMBER_JOINED | INVITATION_DECLINED, occurredAt, readAt (nullable), actor: UserSummary (nullable), group: GroupRef (nullable), reference: uuid }`; 400, 401.
  - `POST /notifications/{id}/read` → 204 (404 `NOTIFICATION_NOT_FOUND`); `POST /notifications/read-all` → 204.
  - Schemas `UserSummary { userId, displayName, username }`, `GroupRef { id, name }`; `User` gains `username`.
  - Error codes `ACCOUNT_NOT_FOUND`, `ALREADY_INVITED`, `NOTIFICATION_NOT_FOUND` with responses and examples (Prism serves the examples).

- [ ] **Step 1:** Edit the contract as listed, with an `example` on every operation and every new error response, and the three codes in the error-code enum.
- [ ] **Step 2:** Run `npx @redocly/cli lint docs/api/openapi.yaml`. Expected: no errors.
- [ ] **Step 3:** Commit only `docs/api/openapi.yaml` as `contract(group): invite by username or email, pending invitations and the inbox`.

---

### Task 2: Usernames and the lookup port (account)

**Why:** an invitation needs a person to be found by a stable, human name. Keycloak owns it (N4), ShareWise copies it once on first login like email and display name (D10), and `UserProfiles` gets the exact lookup. Practises: identity provider vs own data, one identity type (`UserId`).

**Files:**
- Modify: `docker/keycloak/sharewise-realm.json`, `<pkg>/account/domain/Account.java`, `<pkg>/account/persistence/AccountRepository.java`, `<pkg>/account/api/UserProfile.java`, `<pkg>/account/api/UserProfiles.java`, `<pkg>/account/application/AccountService.java`, `<pkg>/account/application/UserProfilesImpl.java`, `<pkg>/account/web/UserResponse.java`
- Create: `<pkg>/account/domain/Username.java`, `<res>/db/migration/account/V106__account_username.sql`
- Test: `<test>/account/domain/UsernameTest.java`, `AccountTest.java`, `AccountFixture.java`, `<test>/account/application/AccountServiceTest.java`, `UserProfilesImplTest.java`, `<test>/account/web/AccountControllerTest.java`, `<test>/KeycloakUsers.java`, `KeycloakUsersTest.java`
- Docs: `RUNNING.md`, `docs/2026-10-30-keycloak-auth.md` (claim list), `docs/conventions/01_ARCHITECTURE_AND_NAMING.md` section 3.4, `docs/diagram-deltas.md`, `docs/domain/ShareWise_Domain_Model.drawio`

**Interfaces:**
- Produces:
  - `record Username(String value)` in `account.domain`; compact constructor strips and lower-cases, then throws `DomainException(ErrorCode.BAD_REQUEST, ...)` unless 3 to 30 characters of `[a-z0-9._-]`.
  - `Account.registerFromIdentityProvider(UserId id, Email email, Username username, DisplayName displayName, Instant now)`; `Account.username(): Username`.
  - `record UserProfile(UserId userId, String displayName, String username, boolean isActive)`.
  - `Optional<UserProfile> UserProfiles.findByIdentifier(String identifier)`: trim, lower-case, `@` present → by email, else by username; inactive accounts are not found; works without a transaction.
  - `UserResponse(UUID userId, String displayName, String username)`.
  - Test: `KeycloakUsers.Login(String subject, String email, String username, String accessToken)`; `KeycloakUsers.create(String displayName)` generates a unique valid username; new overload `create(String displayName, String username)` that throws `IllegalStateException` when Keycloak refuses; `AccountFixture.withUsername(String)`, `username()`; fixed usernames `alice`, `bob`, `carol`, `david`.

- [ ] **Step 1: Write the failing tests** (names and assertions):
  - `UsernameTest.lowerCasesAndTrims`: `new Username("  Alice.B-1 ").value()` is `alice.b-1`.
  - `UsernameTest.refusesWhatIsNotAUsername` (`@ParameterizedTest`): `""`, `"ab"`, 31 characters, `"bob@example.com"`, `"a b"`, `"ali/ce"` each throw `DomainException` with `BAD_REQUEST`.
  - `AccountServiceTest.firstLoginStoresTheUsernameFromTheToken`: claims with `preferredUsername "alice1"` → the saved account's `username()` is `alice1` and the returned profile carries it.
  - `AccountServiceTest.firstLoginWithAnInvalidUsernameIs401AndSavesNothing`: `preferredUsername "bob@example.com"` → `DomainException` `UNAUTHENTICATED`; `insertIfAbsent` never called.
  - `UserProfilesImplTest.findsByUsernameOrByEmailIgnoringCaseAndSpaces`: sign up a user; `findByIdentifier(" " + username.toUpperCase())` and the email in other case both return that profile.
  - `UserProfilesImplTest.unknownBlankAndWildcardIdentifiersFindNobody` (`@ParameterizedTest`): `""`, `"   "`, `"%"`, `"_"`, `"ali%"`, `"nobody-here"`, `"@"` → empty. Called with no transaction (pitfall 5).
  - `UserProfilesImplTest.anInactiveAccountIsNotFound`: set the row's status to a non-ACTIVE `AccountStatus` constant with `JdbcTemplate`, then `findByIdentifier` is empty.
  - `UserProfilesImplTest.aKeycloakUserWhoNeverCalledMeIsNotFoundUntilTheFirstRequest`: `KeycloakUsers.create` only → empty; `GET /me` with its token → found.
  - `AccountControllerTest.meReturnsTheUsername`: `$.username` equals the fixture's username.
  - `KeycloakUsersTest.theRealmRefusesAUsernameWithAnAtSign`: `create("Bob", "bob@example.com")` throws; proves the realm rule works.
- [ ] **Step 2:** Run `./mvnw -q test -Dtest='UsernameTest,AccountServiceTest,UserProfilesImplTest,AccountControllerTest,KeycloakUsersTest'`. Expected: compile errors or FAIL.
- [ ] **Step 3: Implement.** Domain and persistence first: `Username`, the new `Account` factory and field, migration `V106` (add `username VARCHAR(30) NOT NULL` with a unique index `accounts_username_unique`, a `--` comment saying why and that databases are recreated), `AccountRepository.insertIfAbsent` with the extra column plus `findByUsername(String)` and `findByEmail(String)` derived queries. Then `AccountService.resolveOrCreate` (invalid username → `unauthenticated()`; the lost-race log line names "email or username"), `UserProfilesImpl.findByIdentifier` (`@Transactional(readOnly = true)`), `UserProfile`, `UserResponse`. Then the realm file: `registrationEmailAsUsername` false, `editUsernameAllowed` false, and the user-profile username validator with the same pattern; verify it through the Keycloak test container (the realm test above), not by reading the JSON. Then the test helpers (`KeycloakUsers` creates the user with the generated username and requests the token with that username; every `new UserProfile(` in tests gets the new argument).
- [ ] **Step 4:** Run the Step 2 command, then `./mvnw -q verify`. Expected: PASS. Update `RUNNING.md` (delete the local database and the Keycloak volume once; the sign-up form now asks for a username), the keycloak design doc claim line, conventions 3.4 (`Account.username`), the diagram (`Account.username`) and a new row in `docs/diagram-deltas.md`.
- [ ] **Step 5: Commit** `feat(account): usernames from Keycloak and an exact lookup port`, then `docs(account): ...` only if the docs were not in the same commit (they should be).

---

### Task 3: Targeted invitations, decline, pending list (group)

**Why:** `Group` owns `Invitation`, so every invitation rule stays in the root and under its row lock (aggregate rules, document 01 section 2.3). Status is derived so no reader can see a stale `PENDING` (design review, section 3). Practises: aggregate boundary, pessimistic locking as the single guard, state as a query.

**Files:**
- Modify: `<pkg>/group/domain/Invitation.java`, `Group.java`, `InvitationStatus.java` (Javadoc), `<pkg>/group/api/MemberInvited.java`, `MemberJoined.java`, `<pkg>/group/persistence/GroupRepository.java`, `<pkg>/group/application/GroupService.java`, `<pkg>/group/web/GroupController.java`, `InvitationResponse.java`, `<pkg>/shared/ErrorCode.java`
- Create: `<pkg>/group/api/InvitationDeclined.java`, `<pkg>/group/web/InviteMemberRequest.java`, `PendingInvitationResponse.java`, `UserSummaryResponse.java`, `GroupRefResponse.java`, `<res>/db/migration/group/V205__invitations_for_accounts.sql`
- Test: `<test>/group/domain/GroupTest.java`, `GroupFixture.java`, `<test>/group/application/GroupServiceTest.java`, `<test>/group/web/InvitationControllerTest.java`, `GroupRequests.java`, `GroupControllerTest.java`
- Docs: `docs/conventions/01_ARCHITECTURE_AND_NAMING.md` (3.1: `Invitation` has `invitee`, events), `docs/diagram-deltas.md` (rewrite row 2, add `MemberJoined.invitedBy`, derived `EXPIRED`), the diagram

**Interfaces:**
- Consumes (Task 2): `UserProfiles.findByIdentifier(String)`, `UserProfile.username()`.
- Produces:
  - `Invitation.issue(UserId invitee, UserId createdBy, Instant now)` (package-private), `Invitation.statusAt(Instant now): InvitationStatus` (`PENDING` stored and `now` not after `expiresAt` → `PENDING`; stored `PENDING` past it → `EXPIRED`; else the stored status), `invitee()`, `createdBy()`, `createdAt()`, `expiresAt()`; no token, no email.
  - `Group.inviteMember(UserId invitee, UserId inviter, Instant now): Invited` with checks in this order `NOT_A_MEMBER`, `ALREADY_MEMBER`, `ALREADY_INVITED`; `Group.acceptInvitation(InvitationId id, UserId user, Instant now): MemberJoined`; `Group.declineInvitation(InvitationId id, UserId user, Instant now): InvitationDeclined`; `Group.pendingInvitationsFor(UserId user, Instant now): List<Invitation>`. Accept and decline: not found / not for `user` / stored status not `PENDING` → `INVITATION_NOT_FOUND`; expired → `INVITATION_EXPIRED` (writes nothing). `createGroup`'s `MemberJoined` has `invitedBy` null; accept's has the inviter.
  - `GroupRepository.findIdByInvitationId(UUID): Optional<UUID>` (replaces the token query), `findAllWithPendingInvitationFor(UUID userId, Instant now): List<Group>` (stored `PENDING` and `expiresAt >= now`).
  - `GroupService`: constructor gains `UserProfiles`; `InvitationView inviteMember(GroupId groupId, UserId inviter, String identifier)` (loads with `findByIdForUpdate`; unknown identifier → `DomainException(ACCOUNT_NOT_FOUND, "No account found")`); `GroupView acceptInvitation(InvitationId id, UserId caller)`; `void declineInvitation(InvitationId id, UserId caller)`; `List<PendingInvitationView> listPendingInvitations(UserId caller)` newest first; records `InvitationView(InvitationId id, UserProfile invitee, Instant expiresAt)` and `PendingInvitationView(InvitationId id, GroupId groupId, String groupName, UserId invitedBy, Instant expiresAt)`.
  - `ErrorCode.ACCOUNT_NOT_FOUND(404)`, `ALREADY_INVITED(409)`, `NOTIFICATION_NOT_FOUND(404)`.
  - Test helpers on `GroupRequests`: `invite(UUID group, AccountFixture inviter, AccountFixture invitee): UUID` (invitation id, by username), `createInvitation(UUID group, AccountFixture caller, String identifier): ResultActions`, `accept(UUID invitationId, AccountFixture caller)`, `decline(...)`, `pending(AccountFixture caller)`; `groupWith` uses them.

- [ ] **Step 1: Write the failing domain tests** in `GroupTest` (replace the link/email invitation tests): `aMemberInvitesAnAccountAndTheInvitationIsValidForSevenDays`, `aNonMemberCannotInvite`, `invitingACurrentMemberIsRefused`, `invitingSomebodyWithAnOpenInvitationIsRefused`, `aSecondInvitationIsPossibleOnceTheFirstIsDeclined`, `anInvitationIsExpiredAfterSevenDaysAndTheSamePersonCanBeInvitedAgain` (one second after `expiresAt`), `anInvitationIsStillOpenAtTheExactExpiryInstant` (also: a new invite then is `ALREADY_INVITED`), `acceptingAddsTheInviteeAsMemberAndReturnsMemberJoinedWithInvitedBy`, `somebodyElsesInvitationIsNotFoundAndNobodyJoins`, `acceptingAnExpiredInvitationIs410AndNobodyJoins`, `acceptingTwiceIsNotFoundTheSecondTime`, `decliningReturnsInvitationDeclinedAndNobodyJoins`, `decliningAnExpiredInvitationIsExpired`, `pendingInvitationsAreOnlyTheOpenOnesOfThatUser`, `theCreatorsMemberJoinedHasNoInviter`.
- [ ] **Step 2:** Run `./mvnw -q test -Dtest='GroupTest'`. Expected: compile errors / FAIL.
- [ ] **Step 3: Implement the domain, events, repository queries and migration** `V205` (add `invitee_id UUID NOT NULL`, drop `token` and `email`, add `ix_invitations_invitee`; a `--` comment saying why and that databases are recreated). Javadoc on every operation: rule, `(R9)`, event returned, `@throws` codes. Update `InvitationStatus` Javadoc ("`EXPIRED` is derived, never stored").
- [ ] **Step 4:** Run `GroupTest`. Expected: PASS.
- [ ] **Step 5: Write the failing service tests** in `GroupServiceTest`: `inviteResolvesTheIdentifierLocksTheGroupAndPublishesMemberInvited` (verifies `findByIdForUpdate`, one `MemberInvited` carrying the invitee's `UserId`), `inviteForAnUnknownIdentifierIsAccountNotFoundAndNothingIsSavedOrPublished`, `acceptLocksSavesAndPublishesMemberJoinedWithInvitedBy`, `declineSavesAndPublishesInvitationDeclined`, `aRefusedAcceptSavesAndPublishesNothing`, `pendingInvitationsAreListedNewestFirst`. Implement `GroupService` to make them pass.
- [ ] **Step 6: Write the failing API tests** in `InvitationControllerTest` (real Keycloak users, `MutableClock`): `postCreatesAnInvitationForTheInviteeValidForSevenDays` (201, `$.invitee.username`, `$.expiresAt`; one `MemberInvited` with the invitee id), `postForAnUnknownIdentifierIs404AccountNotFound`, `postWithBlankOrTooLongIdentifierIs400` (`""`, `"   "`, 255 characters), `postWithWildcardOrAtSignIdentifiersFindsNobody` (`"%"`, `"_"`, `"@"` → 404 `ACCOUNT_NOT_FOUND`), `postWithEmailOrUpperCaseUsernameWorks`, `postForAMemberIs409AlreadyMember`, `postTwiceForTheSamePersonIs409AlreadyInvited`, `postAsNonMemberIs403`, `postForAnUnknownGroupIs404`, `pendingListsOnlyTheCallersOpenInvitationsNewestFirstWithGroupAndInviter` (another person's invitation never appears; one expired one is absent; one exactly at the expiry instant is present), `acceptMakesTheCallerAMemberAndPublishesMemberJoined`, `acceptingTwiceIs404TheSecondTime`, `acceptingSomebodyElsesInvitationIs404`, `acceptingAfterSevenDaysIs410`, `declineIs204AndTheInvitationIsGoneFromThePendingList`, `decliningAfterSevenDaysIs410`, `everyInvitationEndpointWithoutATokenIs401`. Implement controller, request/response records and the `ErrorCode` entries; the controller fills names and usernames through `UserProfiles.findProfiles` (unknown → "Unknown user", as `getGroup` does).
- [ ] **Step 7:** Run `./mvnw -q verify`. Expected: PASS (includes `ModularityTests`, `ConventionTests`, the formatter: `./mvnw spring-javaformat:apply` first).
- [ ] **Step 8:** Update the docs listed above, grep for the old terms (`token`, `createInvitation`, `link invitation`) in `docs/` and `RUNNING.md`, then commit `feat(group): invite an account, decline and list pending invitations`.

---

### Task 4: The notification module

**Why:** "what happened" events go to a leaf module that keeps one inbox row per recipient (N1); nothing else knows it exists. Practises: bounded context and context map, idempotent consumer, outbox-backed `@ApplicationModuleListener`, ports returning plain values.

**Files:**
- Create: `<pkg>/group/api/GroupDirectory.java`, `<pkg>/group/application/GroupDirectoryImpl.java`; in `<pkg>/notification/`: `package-info.java`, `domain/Notification.java`, `domain/NotificationId.java`, `domain/NotificationType.java`, `persistence/NotificationRepository.java`, `application/NotificationService.java`, `application/MemberJoinedListener.java`, `application/InvitationDeclinedListener.java`, `web/NotificationController.java`, `web/NotificationListResponse.java`, `web/NotificationResponse.java`, plus the notification module's own `web/UserSummaryResponse.java` and `web/GroupRefResponse.java` (web types are never shared between modules); `<res>/db/migration/notification/V501__notifications.sql`
- Modify: `<test>/ModularityTests.java` (module list), `<test>/ConventionTests.java`, `<res>/application.yml` only if the Flyway locations do not already pick up `db/migration/notification` (check first)
- Test: `<test>/group/application/GroupDirectoryImplTest.java`, `<test>/notification/domain/NotificationTest.java`, `<test>/notification/application/NotificationListenersTest.java`, `<test>/notification/web/NotificationControllerTest.java`
- Docs: `docs/conventions/01_ARCHITECTURE_AND_NAMING.md` (module table, dependency line, new vocabulary section for the Notification context, "words we do not use" if any), `02_TESTING.md` section 6 (new rule), `docs/diagram-deltas.md`, the diagram (context Notification: `Notification`, `NotificationType`)

**Interfaces:**
- Consumes: `MemberJoined`, `InvitationDeclined` (Task 3); `UserProfiles.findProfiles(Set<UserId>)`; `ErrorCode.NOTIFICATION_NOT_FOUND`.
- Produces:
  - `Map<GroupId, String> GroupDirectory.findNames(Set<GroupId> groupIds)`; unknown ids left out; no transaction needed.
  - `package-info`: `@ApplicationModule(allowedDependencies = { "account::api", "group::api", "shared" })`; there is no `api` package, because nothing may depend on this module.
  - `NotificationType { MEMBER_JOINED, INVITATION_DECLINED }`; `Notification.notify(UserId recipient, NotificationType type, GroupId groupId, UserId actor, UUID reference, Instant occurredAt): Notification`; `markRead(Instant now)` keeps the first read time; `isRead()`.
  - `NotificationRepository.insertIfAbsent(Notification): boolean` (native `INSERT ... ON CONFLICT DO NOTHING`, true when inserted).
  - `NotificationService`: `boolean record(Notification)`; `NotificationList list(UserId caller, boolean unreadOnly, int limit)` (newest first by `occurredAt`, then id; `limit` outside 1..50 → `DomainException(BAD_REQUEST)`) returning `record NotificationList(long unreadCount, List<NotificationView> items)` and `NotificationView(NotificationId id, NotificationType type, Instant occurredAt, Instant readAt, UserProfile actor, GroupId groupId, String groupName, UUID reference)` (`actor` and `groupName` null when not resolvable); `void markRead(NotificationId id, UserId caller)` (not the caller's → `NOTIFICATION_NOT_FOUND`); `void markAllRead(UserId caller)`.
  - Listeners (package-private, `@ApplicationModuleListener`): `on(MemberJoined)` records `MEMBER_JOINED` for `invitedBy` (actor = `userId`, reference = `userId.value()`) and returns without a row when `invitedBy` is null; `on(InvitationDeclined)` records `INVITATION_DECLINED` for `invitedBy` (actor = `invitee`, reference = `invitationId.value()`). Both take every id from the event and never use `CurrentUser` (pitfall 3).
  - HTTP as in the contract (Task 1): `GET /notifications`, `POST /notifications/{id}/read`, `POST /notifications/read-all`; every call filters on the caller.

- [ ] **Step 1: Write the failing tests:**
  - `GroupDirectoryImplTest.returnsNamesAndLeavesUnknownIdsOut` (`@ModuleIntegrationTest`, no surrounding transaction).
  - `NotificationTest.anEntryIsUnreadUntilItIsMarkedRead`, `markingReadTwiceKeepsTheFirstTime`.
  - `NotificationListenersTest` (integration, calls the listener beans directly in their package): `joiningThroughAnInvitationCreatesAnEntryForTheInviter`, `theCreatorsOwnJoinCreatesNothing`, `theSameEventTwiceCreatesOneEntry`, `aPersonJoiningTwiceAtDifferentTimesCreatesTwoEntries`, `aDeclineCreatesAnEntryForTheInviter`.
  - `NotificationControllerTest`: `acceptedInvitationShowsAJoinedEntryWithActorGroupAndUnreadCount` (real flow, Awaitility on Alice's `GET /notifications`: type `MEMBER_JOINED`, `$.actor.username`, `$.group.name`, `$.unreadCount` 1), `unreadFilterListsOnlyUnreadEntries`, `readMarksOneEntryAndTheCountDrops`, `readAllMarksOnlyTheCallersEntries` (Bob's entry stays unread), `anotherPersonsEntryIs404NotificationNotFoundOnRead`, `anEntryWhoseActorIsUnknownHasNullActor` (record one through the service with an unknown actor id), `limitOutsideOneToFiftyIs400` (0, 51, -1), `everyNotificationEndpointWithoutATokenIs401`.
  - `ConventionTests.nothingDependsOnTheNotificationModule` (ArchUnit: no class outside `..notification..` depends on a class inside it); `ModularityTests.detectsTheFourContextsAndSharedTypes` now lists `notification` as well (rename to "…FiveContexts…" if the name misleads).
- [ ] **Step 2:** Run `./mvnw -q test -Dtest='GroupDirectoryImplTest,NotificationTest,NotificationListenersTest,NotificationControllerTest,ModularityTests,ConventionTests'`. Expected: FAIL / compile errors.
- [ ] **Step 3: Implement** in this order: `GroupDirectory` and its impl (`@Transactional(readOnly = true)`, built from `groups.findAllById`); migration `V501` (`notifications`: `id UUID PK`, `recipient_id`, `type VARCHAR(40)`, `group_id`, `actor_id`, `reference UUID`, `occurred_at`, `read_at`; the unique key and the bell index from spec section 6.2; a `--` comment for each); domain; repository; service; listeners; controller (`limit` default 10, `unread` default false, 204 for the two POSTs); `package-info`.
- [ ] **Step 4:** Run the Step 2 command, then `./mvnw -q verify`. Expected: PASS.
- [ ] **Step 5:** Update the docs listed above; commit `feat(notification): inbox module fed by member joined and invitation declined` (the docs in the same commit).

---

### Task 5: Frontend (invite box, bell, route handlers, e2e)

**Why:** the user-visible part: an invite box on the group page and a bell that merges the two sources (N6, N9). Practises: BFF merge in a route handler, a poll with a stop condition, text-only rendering of user-supplied names.

**Files:**
- Generated: `frontend/src/api/schema.d.ts` via `npm run gen:api` (never by hand)
- Create: `frontend/src/components/InviteForm.tsx` + `InviteForm.test.tsx`, `InboxBell.tsx` + `InboxBell.test.tsx`; `frontend/src/app/api/inbox/route.ts` + `route.test.ts`; `frontend/src/app/api/invitations/[invitationId]/accept/route.ts`, `.../decline/route.ts`; `frontend/src/app/api/notifications/[id]/read/route.ts`, `frontend/src/app/api/notifications/read-all/route.ts`
- Modify: `frontend/src/components/AuthBar.tsx` (render `<InboxBell />` for a signed-in user), `GroupDetail.tsx` (replace `InviteButton` with `InviteForm`), `frontend/src/lib/errors.ts` + `errors.test.ts`, `frontend/e2e/mallorca.spec.ts`
- Delete: `InviteButton.tsx` + test, `JoinGroup.tsx` + test, `frontend/src/app/join/[token]/page.tsx`, `frontend/src/app/api/invitations/[token]/accept/route.ts`
- Docs: `RUNNING.md` (demo steps), `docs/conventions/04_FRONTEND.md` only if a rule changes

**Interfaces:**
- Consumes: the contract of Task 1 (types from the regenerated schema: `components["schemas"]["PendingInvitation"]`, `NotificationList`, `UserSummary`).
- Produces:
  - `GET /api/inbox` → 200 `{ invitations: PendingInvitation[] | null, notifications: NotificationList | null }`; calls `proxyToBackend` for `/invitations` and `/notifications?limit=10`; a part whose backend call failed with something other than 401 is `null`; a 401 from either is passed on unchanged (including its cookie headers); a refreshed session cookie from either response is forwarded.
  - `InviteForm({ groupId }: { groupId: string })`: one text input labelled "Username or email", button "Invite", posts `{ identifier }` trimmed to `/groups/${groupId}/invitations`, shows "Invitation sent to @<username>" on success and `messageFor(error)` in an alert on failure.
  - `InboxBell()`: button with accessible name "Inbox" and a badge (pending invitations + unread entries; hidden at 0); dropdown with pending invitations first (text "<displayName> invited you to <group name>", buttons Accept and Decline), then entries (`MEMBER_JOINED`: "<name> joined <group>", `INVITATION_DECLINED`: "<name> declined your invitation to <group>", with "someone" / "a group" fallbacks), and a "Mark all as read" button; Accept → `POST /invitations/{id}/accept` then `router.push("/groups/{groupId}")`; Decline → `POST .../decline`; clicking an unread entry → `POST /notifications/{id}/read`. Polls `/inbox` every 30 000 ms and on `visibilitychange` to visible; stops after a 401.
  - New messages in `errors.ts`: `ACCOUNT_NOT_FOUND`: "No account found. The person must have signed in to ShareWise once.", `ALREADY_INVITED`: "This person already has an open invitation.", `NOTIFICATION_NOT_FOUND`: "This entry no longer exists."; changed: `INVITATION_NOT_FOUND`: "This invitation is no longer valid.", `INVITATION_EXPIRED`: "This invitation has expired. Ask for a new one."

- [ ] **Step 1:** Run `npm run gen:api` and confirm `npm run typecheck` shows only the expected breaks (the removed link components).
- [ ] **Step 2: Write the failing tests** (Vitest + Testing Library, `api` mocked as in `InviteButton.test.tsx`):
  - `InviteForm`: `sendsTheIdentifierTrimmedAndShowsTheInvitedUsername`; `showsAMessageForEachRefusal` (`it.each` over `ACCOUNT_NOT_FOUND`, `ALREADY_MEMBER`, `ALREADY_INVITED`, `NOT_A_MEMBER`); `disablesTheButtonWhileSending`.
  - `InboxBell` (fake timers where needed): `badgeIsPendingInvitationsPlusUnreadEntries`; `listsInvitationsBeforeEntriesWithAcceptAndDecline`; `acceptingPostsThenGoesToTheGroupAndDisablesTheButtonWhileRunning`; `a404AfterAcceptCountsAsDone` (row disappears, no error shown); `decliningRemovesTheInvitation`; `clickingAnEntryMarksItReadAndMarkAllReadClearsTheBadge`; `showsWhatLoadedWhenOneOfTheTwoPartsIsNull`; `rendersNamesAsTextNotHtml` (group name `<img src=x onerror=alert(1)>` appears as text, no `img` element); `stopsPollingAfter401`; `pollsEveryThirtySecondsAndWhenTheTabBecomesVisible`.
  - `/api/inbox` route test: `returnsBothPartsWhenBothAnswer`, `anAnswerOfNullForAFailedPart`, `a401FromEitherIsPassedOn`, mocking `proxyToBackend`.
  - `errors.test.ts`: the five new or changed messages.
- [ ] **Step 3:** Run `npm test`. Expected: FAIL.
- [ ] **Step 4: Implement** the components, route handlers (same shape as the existing ones: `await connection()`, `proxyToBackend`), the deletions, the `errors.ts` entries, `AuthBar` and `GroupDetail` wiring. Names are rendered as React text only.
- [ ] **Step 5:** Update `frontend/e2e/mallorca.spec.ts`: the Keycloak registration fills "Username" too (`${firstName.toLowerCase()}${RUN}`); all three sign up first; Alice invites Bob and Carol by username in the invite box on the Members tab; each of them opens the bell, sees "Alice … invited you to Mallorca 2027" and clicks Accept; Alice's bell later shows "joined" entries. The rest of the scenario is unchanged.
- [ ] **Step 6:** Run `npm run lint && npm run typecheck && npm test && npm run build`. Expected: PASS. With backend and Postgres running: `npm run e2e`. Expected: PASS.
- [ ] **Step 7:** Commit `feat(frontend): invite by username or email and a bell for invitations and entries` (regenerated `schema.d.ts` included).

---

### Task 6: Closing sweep

**Why:** the definition of done in `docs/conventions/README.md`; catches drift between code and docs.

- [ ] **Step 1:** Run the full gates: `cd backend && ./mvnw -q clean verify`, `cd ../frontend && npm run lint && npm run typecheck && npm test && npm run build`, `npx @redocly/cli lint docs/api/openapi.yaml`. Expected: all green.
- [ ] **Step 2:** Grep the repo (excluding `node_modules`, `target`, `docs/archive`) for the old terms: `invitation link`, `/join/`, `JoinGroup`, `InviteButton`, `findIdByInvitationToken`, `invitationWithToken`, `INVITATION_RECEIVED`. Fix every live mention; historical design docs keep theirs.
- [ ] **Step 3:** Confirm the diagram and `docs/diagram-deltas.md` list: `Account.username`, `Invitation.invitee` and derived `EXPIRED`, `MemberInvited.invitee`, `MemberJoined.invitedBy`, `InvitationDeclined`, context Notification, the pending-invitation read model, `GroupDirectory`. `RUNNING.md` explains the deleted database and the new sign-up form.
- [ ] **Step 4:** Run `/review-branch` (conventions, diagram names, fresh reviewer) and `/edge-case-probe` attack mode on the invitation and inbox endpoints; fix the top findings test-first, park the rest in the design doc.
- [ ] **Step 5:** Merge with `git merge --no-ff` and a message `merge: feature/notifications (usernames, targeted invitations, notification inbox)`.

---

## Self-review notes
- **Spec coverage:** usernames and realm (Task 2), lookup port (2), derived status and lock and decline and pending query and events (3), `GroupDirectory` and notification module and the two listeners (4), `/api/inbox` merge and bell and invite box and e2e (5), docs and diagram per task, parked F29 and F30 are in the spec's section 8 and are not tasks here. The payment, waiver and removal entries are intentionally outside this plan (N8).
- **One gap decided here and written back into the spec (6.4):** the error code for another person's notification id is `NOTIFICATION_NOT_FOUND` (a plan call).
- **Known cost:** Task 3 is the largest commit because changing the `Group` signatures forces the service and web layers to change in the same build.
