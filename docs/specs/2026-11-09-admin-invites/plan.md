# Admin-only invitations and promotion to admin: implementation plan

> **For agentic workers:** use superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Only admins invite; the inviter picks the invitee's role; admins can promote members (one way); the promoted person gets an inbox entry.

**Architecture:** All rules live in the `Group` aggregate (`requireAdmin`, `inviteMember(role)`, `promoteToAdmin`). The service keeps its thin locked-group shape. Notification gets one more listener. Contract first, in its own commit.

**Tech Stack:** Java 21, Spring Boot, JPA, Flyway; Next.js, TypeScript, Vitest.

**Spec:** `docs/specs/2026-11-09-admin-invites/design.md` (read it first; decisions A1 to A6).

## Global Constraints
- Names: `NOT_AN_ADMIN` (403), `MEMBER_NOT_FOUND` (404), `ALREADY_ADMIN` (409), `MEMBER_PROMOTED`, `MemberPromoted(groupId, userId, promotedBy, occurredAt)`, `GroupRole {ADMIN, MEMBER}`.
- Commit format `<type>(<scope>): <subject>`, trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. The contract is its own `contract:` commit. Never `--no-verify`.
- Keep existing line endings; edit with the edit tools; check `git diff --stat`.
- Migration `V206__invitation_role.sql` (group range); `main` is never edited in a migration.
- Format with `./mvnw spring-javaformat:apply` before committing backend code. Docker must run.

## Review Focus
- Member (not admin) invites: 403 `NOT_AN_ADMIN`, nothing saved (Task 2, 3).
- Request without `role` joins as `MEMBER` (Task 2, 3).
- Promote an outsider or former member: `MEMBER_NOT_FOUND`; promote an admin or self: `ALREADY_ADMIN` (Task 2, 3).
- A rejected promotion leaves role unchanged and publishes nothing (Task 2, 3).
- Promotion reaches only the promoted person's inbox (Task 4).

---

### Task 1: Contract
**Files:** Modify `docs/api/openapi.yaml`, `frontend/src/api/schema.d.ts` (generated, `npm run gen:api`).
- [ ] Add `role` (`ADMIN|MEMBER`, optional, default `MEMBER`) to `InviteMemberRequest`; required `role` on `PendingInvitation`; 403 `NOT_AN_ADMIN` on `createInvitation` and "caller must be an admin" in its description.
- [ ] Add `POST /groups/{groupId}/members/{userId}/promote` (`operationId: promoteToAdmin`, no body, 200 `Member`; 403 `NOT_A_MEMBER`/`NOT_AN_ADMIN`, 404 `GROUP_NOT_FOUND`/`MEMBER_NOT_FOUND`, 409 `ALREADY_ADMIN`). Add the three codes to the `ErrorCode` enum and `MEMBER_PROMOTED` to `NotificationType`.
- [ ] Run `npx @redocly/cli lint docs/api/openapi.yaml` (passes), `cd frontend && npm run gen:api`.
- [ ] Commit `contract(group): admin-only invitations, invitation role, promote member`; commit regenerated types separately as `chore(frontend): regenerate api types`.

### Task 2: Group domain
**Files:** Modify `group/domain/Group.java`, `Invitation.java`, `Membership.java`, `group/api/MemberInvited.java`, `shared/ErrorCode.java`; create `group/api/MemberPromoted.java`; create `db/migration/group/V206__invitation_role.sql`; test `group/domain/GroupTest.java`.

**Interfaces — Produces:**
- `Group.requireAdmin(UserId)` (replaces `requireActiveMember`): `NOT_A_MEMBER`, then `NOT_AN_ADMIN`.
- `Group.inviteMember(UserId invitee, GroupRole role, UserId inviter, Instant now): Invited`.
- `Group.promoteToAdmin(UserId user, UserId promotedBy, Instant now): MemberPromoted`.
- `MemberInvited(groupId, invitationId, invitee, invitedBy, role, occurredAt)`.

- [ ] Write failing tests in `GroupTest`: `adminInvitesWithTheChosenRoleAndAcceptJoinsWithIt`; `aMemberWhoIsNotAdminCannotInvite` (`NOT_AN_ADMIN`, no invitation added); `aNonMemberCannotInvite` (`NOT_A_MEMBER`); `promoteMakesAMemberAdminAndReturnsTheEvent`; `promoteByANonAdminIsRefusedAndChangesNothing`; `promoteOfAnOutsiderOrFormerMemberIsMemberNotFound`; `promoteOfAnAdminIsAlreadyAdmin`. Update the existing `requireActiveMember*` tests to `requireAdmin` and fix existing invite tests so the inviter is an admin.
- [ ] Run `./mvnw -q test -Dtest=GroupTest` — fails to compile / fails.
- [ ] Implement: `Invitation.role` column (`@Enumerated`, `issue(invitee, role, createdBy, now)`, `role()`); `Membership.promote()` package-private; `Group` methods as above; accept uses `invitation.role()` for the `Membership` and `MemberJoined`; add error codes to `ErrorCode` with their statuses. Migration adds `role VARCHAR(10) NOT NULL DEFAULT 'MEMBER'` with a why-comment.
- [ ] Run `./mvnw -q test -Dtest=GroupTest` — passes. Commit `feat(group): admin-only invitations with role and promoteToAdmin`.

### Task 3: Service and web
**Files:** Modify `group/application/GroupService.java`, `group/web/GroupController.java`, `InviteMemberRequest.java`, `PendingInvitationResponse.java`, `GroupService.PendingInvitationView`, `group/persistence/PendingInvitationRow.java` and the pending query in `GroupRepository.java` (add role); tests `GroupServiceTest`, `web/InvitationControllerTest`, `web/GroupRequests`, `MallorcaSliceTest` if its inviter is not admin.

**Interfaces — Consumes:** Task 2 domain. **Produces:** `GroupService.inviteMember(GroupId, UserId inviter, String identifier, GroupRole role)`; `GroupService.promoteToAdmin(GroupId, UserId caller, UserId user): MemberView`; `POST /groups/{groupId}/members/{userId}/promote`.

- [ ] Failing tests: service — invite calls `requireAdmin` before the account lookup, a rejected invite saves/publishes nothing; promote locks, saves, publishes `MemberPromoted`. Controller — member invite is 403 `NOT_AN_ADMIN`; invite with `role: ADMIN` then accept gives an ADMIN in `GET /groups/{id}`; no `role` gives MEMBER; promote 200 with role ADMIN, 409 `ALREADY_ADMIN`, 404 `MEMBER_NOT_FOUND`, 403 for a member; `MemberPromoted` recorded; pending invitation JSON has `role`.
- [ ] Run `./mvnw -q test -Dtest='GroupServiceTest,InvitationControllerTest'` — fails.
- [ ] Implement service methods (locked group → one group method → save → publish → view), `role` default `MEMBER` in the request record, controller endpoint, pending-invitation role through the query row.
- [ ] Run `./mvnw -q verify` (whole backend, incl. `ModularityTests`, `ConventionTests`, Mallorca) — passes. Commit `feat(group): invite role and promote endpoint`.

### Task 4: Notification
**Files:** Modify `notification/domain/NotificationType.java`, `notification/application/NotificationService.java`; create `notification/application/MemberPromotedListener.java`; tests in the existing notification service and controller tests.

**Interfaces — Consumes:** `MemberPromoted` from `group.api`. **Produces:** type `MEMBER_PROMOTED` (recipient `userId`, actor `promotedBy`, reference `userId`).

- [ ] Failing tests: service creates one entry for the promoted person, none for the promoter, redelivery is a no-op; controller (Awaitility) promote in a group shows `MEMBER_PROMOTED` in the promoted person's `GET /inbox` and not in the promoter's.
- [ ] Run the notification tests — fail.
- [ ] Implement `MemberPromotedListener` (one `@ApplicationModuleListener` method delegating to a new `NotificationService` method, same shape as `MemberJoinedListener`), the enum value with Javadoc.
- [ ] Run `./mvnw -q verify` — passes. Commit `feat(notification): tell a member they were made admin`.

### Task 5: Frontend
**Files:** Modify `components/GroupDetail.tsx`, `InviteForm.tsx`, `MemberList.tsx`, `InboxBell.tsx`, the invitations list component (where pending invitations render), `lib/errors.ts`; route handler for promote if `app/api/groups/[groupId]` does not already proxy the path; tests beside each component.

- [ ] Failing tests: `GroupDetail` renders `InviteForm` only for an ADMIN caller; `InviteForm` sends `role` (default MEMBER, ADMIN when chosen); `MemberList` shows "Make admin" only to admins and only on non-admin rows, click calls the promote path and reports success; `InboxBell` renders the `MEMBER_PROMOTED` text; pending invitation shows "as admin"; `messageFor` has texts for the three new codes.
- [ ] Run `cd frontend && npm test` — fails.
- [ ] Implement with existing helpers (`api<T>()`, `ErrorBanner`, `messageFor`); follow the dark-mode rules of document 04 section 5.
- [ ] Run `npm run lint && npm run typecheck && npm test && npm run build` — passes. Commit `feat(frontend): admin-only invite form, role choice, make admin`.

### Task 6: Docs and diagram
**Files:** Modify `docs/domain/ShareWise_Domain_Model.drawio`, `docs/diagram-deltas.md`, `docs/conventions/01_ARCHITECTURE_AND_NAMING.md` (3.1, 3.6), `RUNNING.md` if demo steps mention inviting; grep for "any member may invite" and fix.
- [ ] Update, then `grep -rn -i "any active member\|any member may" docs backend/src` shows no stale statement. Commit `docs(group): diagram and conventions for invitation role and promotion`.
