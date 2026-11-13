# Admin-only invitations and promotion to admin (design)

Date: 2026-11-09. Branch `feature/admin-invites`, started from `main` in its own worktree so the open `feature/settlements` work is untouched.

## Why

Today `Group.inviteMember` lets **any active member** invite, and everybody joins as `MEMBER`. There is no way to make anybody an admin except creating the group: `promoteToAdmin` and `MemberPromoted` are in the diagram and in `docs/conventions/01` section 3.1, but nothing implements them. Two gaps found while using the app:

1. A normal member can grow the group. Admins should decide who joins.
2. Nobody can become an admin, neither at invitation nor later (requirement J10 / R12: "promote a member to admin" is a **Must**).

**Source of the rule.** The requirements (`docs/domain/ShareWise_Requirements.md`, J3) say a *member* invites. Admin-only invitation is **our choice** (the user's), not something from the professor's slides. Promotion is a Must (J10) and R12 needs it (the last admin cannot leave without promoting someone). Say this when defending.

## Decisions (taken with the user)

| # | Decision | Why |
|---|---|---|
| A1 | Only an **admin** may invite. A member who calls the endpoint gets **403 `NOT_AN_ADMIN`**. | Admins control who is in the group. |
| A2 | The frontend **hides** the invite form from non-admins. The backend refusal (A1) is the real rule; the hiding is only for looks. | A hidden button is not security. |
| A3 | The inviting admin chooses the **role** the invitee gets: `MEMBER` (default) or `ADMIN`. It is stored on the `Invitation` and applied when the invitee accepts. | The role is decided by the person who knows the invitee, at the moment of inviting. |
| A4 | An admin can **promote** an active member to `ADMIN` later (`promoteToAdmin`, event `MemberPromoted`). | Diagram operation, J10. |
| A5 | Promotion is **one way**. No demotion. | The diagram has no demote operation; a group can never end up without an admin. |
| A6 | The promoted person gets an inbox entry `MEMBER_PROMOTED`. | The user wants them to hear about it. |

## Goals, non-goals

**Goals:** A1 to A6, tested at the right levels, contract first, diagram and conventions updated in the same change.

**Non-goals (parked, each with the trigger that brings it back):**
- Demote an admin: when requirements ask for it.
- Remove member / leave group and the last-admin rule R12: the exercise that builds `removeMember` and `leaveGroup`. One-way promotion already makes R12 satisfiable later.
- Cancel or change an open invitation (to correct a wrongly chosen role): when invitations get a "cancel" operation. Until then a wrong role is fixed by promoting after joining, or by waiting 7 days.
- Group status rules (closed or archived groups): groups cannot be closed yet.
- **Anti-goal:** no second authorization mechanism. The rule lives once, in `Group`.

## Design

### Overview

```
admin ──POST /groups/{g}/invitations {identifier, role?}──► GroupService.inviteMember
        Group.requireAdmin(inviter)  ──► NOT_A_MEMBER / NOT_AN_ADMIN (403)
        lookup account ──► Group.inviteMember(invitee, role, inviter) ──► MemberInvited(role)

invitee ──POST /invitations/{i}/accept──► Group.acceptInvitation ──► Membership(role of the invitation)
                                                                └► MemberJoined(role, invitedBy)

admin ──POST /groups/{g}/members/{u}/promote──► GroupService.promoteToAdmin  (group row locked)
        Group.promoteToAdmin(user, promotedBy) ──► MemberPromoted
                                                    └► (async) Notification MEMBER_PROMOTED for the promoted person
```

### Group context (domain)

- `Invitation` gets `role: GroupRole` (the diagram's `Invitation` gets the field, a delta, see below). `Invitation.issue(invitee, role, createdBy, now)`.
- `Group.requireAdmin(UserId)` replaces `requireActiveMember` (used only by invite): not an active member is `NOT_A_MEMBER`, a member who is not `ADMIN` is `NOT_AN_ADMIN`. Order stays: group → caller membership and role → account lookup, so a non-member still learns nothing about which accounts exist.
- `Group.inviteMember(invitee, role, inviter, now)`: unchanged rules (ALREADY_MEMBER, ALREADY_INVITED), stores the role, event `MemberInvited` gets `role`.
- `Group.acceptInvitation`: the new `Membership` gets `invitation.role()`; `MemberJoined.role` already exists and now carries it.
- `Group.promoteToAdmin(UserId user, UserId promotedBy, Instant now)` returns `MemberPromoted(groupId, userId, promotedBy, occurredAt)`. Checks in order: `promotedBy` is an active admin (`NOT_A_MEMBER`, `NOT_AN_ADMIN`), `user` is an active member (`MEMBER_NOT_FOUND`), `user` is not yet admin (`ALREADY_ADMIN`). `Membership.promote()` is package-private.
- `GroupMembershipView.admins` is built from memberships on every call, so other modules see a promotion without a change.

### Application

- `GroupService.inviteMember(groupId, inviter, identifier, role)`: the same locked-group shape as today with `requireAdmin`.
- `GroupService.promoteToAdmin(groupId, caller, userId)`: `lockedGroup` → `group.promoteToAdmin` → save → publish → return the member list entry. The lock serializes two admins promoting the same person (the second gets `ALREADY_ADMIN`) and an invite racing a promotion.

### Contract (`docs/api/openapi.yaml`, own commit first)

- `InviteMemberRequest`: new optional `role` (`ADMIN | MEMBER`, default `MEMBER`).
- `POST /groups/{groupId}/invitations`: description "the caller must be an admin"; new 403 `NOT_AN_ADMIN`.
- `PendingInvitation`: new required `role` (the invitee sees what they are invited as).
- New `POST /groups/{groupId}/members/{userId}/promote`, no body, `200` with `Member`; errors 403 `NOT_A_MEMBER` / `NOT_AN_ADMIN`, 404 `GROUP_NOT_FOUND` / `MEMBER_NOT_FOUND`, 409 `ALREADY_ADMIN`. Action-style like `accept` and `decline`, because promotion is one way and not a general "set role" (call).
- `NotificationType`: add `MEMBER_PROMOTED`.
- `ErrorCode`: add `NOT_AN_ADMIN` (403), `MEMBER_NOT_FOUND` (404), `ALREADY_ADMIN` (409), in the contract, the enum and `frontend/src/lib/errors.ts` (document 03, section 3).

### Database

`V206__invitation_role.sql` (group range): `ALTER TABLE invitations ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'MEMBER'`. The default also covers invitations that are open at deploy time: they stay `MEMBER`, which is what they meant. Check the open branches for a clash on V206 before committing. The notification table needs **no** migration: `type` is a free `VARCHAR(40)`.

### Notification context

New `MEMBER_PROMOTED`. Recipient: the promoted person; actor: the admin who promoted; reference: the promoted person's id (as for `MEMBER_JOINED`; `occurred_at` is part of the unique key). One more asynchronous listener `MemberPromotedListener` delegating to `NotificationService`, like the existing ones. Nobody is notified when someone *joins* as admin: `MemberJoined` already says so to the inviter. No new module dependency (`notification → group::api` exists).

### Frontend

- `GroupDetail` finds the caller in `group.members`; `InviteForm` is rendered only when that member is `ADMIN`.
- `InviteForm` gets a role choice (Member, default / Admin).
- `MemberList` shows a "Make admin" button next to every non-admin member, for admins only; after success the group reloads. Errors go through `ErrorBanner` and `messageFor`.
- The pending-invitations list shows "as admin" when the role is `ADMIN`.
- Inbox text for `MEMBER_PROMOTED`: "<actor> made you an admin of <group>".
- A route handler for the promote call if the existing proxy does not already cover the path (check while planning).
- Dark-mode rules of document 04 section 5 apply to every new element.

### Diagram and docs (same change)

- Diagram: `Invitation.role`, `MemberInvited.role`, and the already drawn `promoteToAdmin` / `MemberPromoted` get their fields (`userId`, `promotedBy`). Note it in `docs/diagram-deltas.md`.
- Conventions 01 section 3.1 (Invitation fields, `MemberPromoted` fields) and 3.6 (`MEMBER_PROMOTED`), `RUNNING.md` if the demo steps change, the review guide `docs/review/<feature>/REVIEW.md` through `/review-doc`.

## Failure modes (first pass; `/edge-case-probe` plan mode completes it)

| Case | Result |
|---|---|
| Member (not admin) posts an invitation | 403 `NOT_AN_ADMIN`, nothing saved, no event |
| Non-member posts an invitation | 403 `NOT_A_MEMBER` before the account lookup |
| Two admins invite the same person together | group row lock: second gets `ALREADY_INVITED` (roles do not merge; first role wins) |
| Two admins promote the same person together | lock: second gets `ALREADY_ADMIN` |
| Promote someone who left, never joined or only has an open invitation | 404 `MEMBER_NOT_FOUND` |
| Promote yourself, or an admin | 409 `ALREADY_ADMIN` |
| Request without `role`, or an invitation open before the migration | `MEMBER` |
| `role` other than `ADMIN` or `MEMBER` | 400 `BAD_REQUEST` (enum deserialization) |
| Invitee accepts an admin invitation | membership is `ADMIN`; `MemberJoined.role = ADMIN` |
| A rejected promotion | no role change, no event, no notification |
| Notification event redelivered | unique key makes it a no-op (existing mechanism) |

## Left to the builder

Exact method placement and test names, the wording of the three new error messages and of the inbox text, whether `MemberList` reloads the group or patches the row, the route handler shape.

## Test plan (one rule, one level)

- `GroupTest` (domain): admin invites with each role; member invite rejected `NOT_AN_ADMIN`; non-member `NOT_A_MEMBER`; accept applies the invitation's role; `promoteToAdmin` happy path, `NOT_AN_ADMIN`, `MEMBER_NOT_FOUND` (outsider and former member), `ALREADY_ADMIN`; rejected calls change nothing.
- `GroupServiceTest`: invite and promote load, lock, save, publish; a rejected call saves and publishes nothing.
- `GroupControllerTest`: status codes and `code`s, `role` in request and response, promote returns the member, the `MemberPromoted` event is recorded.
- `NotificationService` / controller test with Awaitility: a promotion reaches the promoted person's inbox, not the promoter's.
- Frontend: `InviteForm` role choice, `MemberList` button only for admins, `GroupDetail` hides the form for members, inbox text.
- `ConventionTests` / `ModularityTests` stay green; the Mallorca slice stays green (invites there are by the creator, an admin).
