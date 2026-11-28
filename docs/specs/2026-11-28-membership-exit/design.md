# Membership exit: leave a group, remove a member (J10, J11)

Date: 28.11.26 (decisions of 25.11.26). Rules: R11, R12, R13. Diagram operations: `Group.leaveGroup`, `Group.removeMember`; events
`MemberLeft`, `MemberRemoved`; enumeration `MembershipStatus.FORMER_MEMBER` (all already drawn, listed as a known gap in
`docs/diagram-deltas.md`).

## What is built

| Piece | Change |
|---|---|
| `Group.leaveGroup(member, now)` | the caller leaves; returns `MemberLeft` |
| `Group.removeMember(target, actingAdmin, now)` | an admin removes somebody else; returns `MemberRemoved` |
| `Membership` | `leave()` sets `FORMER_MEMBER`; `rejoin(role, now)` makes it `ACTIVE` again |
| `Group.acceptInvitation` | reactivates a former member's membership instead of adding a second row (M3) |
| HTTP | `POST /groups/{groupId}/leave`, `DELETE /groups/{groupId}/members/{userId}`; `GroupDetail.formerMembers` |
| Notification | new type `MEMBER_REMOVED`: the removed person is told who removed them |
| Frontend | Members tab: **Leave group** for the caller, **Remove** next to each other member for admins; the expense list shows "Bob (left)" |

## Decisions

| # | Decision | Why / concept | Who |
|---|---|---|---|
| M1 | A member may leave, and be removed, only when the group's copy of their balance flag says zero (`Membership.hasZeroBalance`, kept up to date from `MemberBalanceStatusChanged`, D12). Otherwise `BALANCE_NOT_ZERO` (409). | R11. Group must not call the settlement context synchronously: settlement already depends on group (`GroupMembership`), a call back would make the module graph cyclic. The flag is the event-fed local copy (eventual consistency, DDD slide 67 rule 4). | rule R11, mechanism exists |
| M2 | The last admin cannot leave while other active members remain: `LAST_ADMIN` (409). The only member may leave; the group then has no active member. | R12 | rule |
| M3 | A former member who is invited again and accepts gets the **same** membership row back (status ACTIVE, the role of the new invitation, the new join time). | one row per person and group (`uq_memberships_group_user`); old expenses keep pointing at the same person | user, 25.11 |
| M4 | Leaving and removal are allowed while the group is OPEN or CLOSED_FOR_SETTLEMENT. ARCHIVED blocks them, which arrives with the lifecycle feature (no archived group can exist before it). | a person who settled can leave while the others still settle up | user, 25.11 |
| M5 | Any admin can remove any other member, admins included. Removing oneself is refused with `CANNOT_REMOVE_YOURSELF` (422); the way out is "Leave", where R12 applies. | one path per intent, so R12 cannot be bypassed by an admin removing themselves | user, 25.11 |
| M6 | `GroupDetail` gains `formerMembers` (same `Member` shape) next to `members`; `members` stays active-only, so every picker (payer, participants, invite) keeps working unchanged. | R13 "Bob (left)" needs the names of people who are no longer in the group | (call) |
| M7 | The removed person gets an inbox entry `MEMBER_REMOVED` (actor: the admin). Leaving creates no entry. | J11 step 3 "Bob is removed and notified"; nobody needs to be told about their own action | (call) |

## Error order

`leaveGroup`: caller is an active member (`NOT_A_MEMBER`), balance zero (`BALANCE_NOT_ZERO`), not the last admin
(`LAST_ADMIN`).

`removeMember`: caller is an admin (`NOT_A_MEMBER`, `NOT_AN_ADMIN`), target is not the caller
(`CANNOT_REMOVE_YOURSELF`), target is an active member (`MEMBER_NOT_FOUND`), target's balance is zero
(`BALANCE_NOT_ZERO`).

Both lock the group row like every other write of the group service, so two admins removing the same member are
serialized and the second gets `MEMBER_NOT_FOUND`.

## Known limit (accepted)

The balance flag lags the ledger by one event delivery. A member who records an expense and leaves within that moment
could leave with a balance. A synchronous check would need the cyclic dependency of M1. The window is the asynchronous
listener's delay (milliseconds in the monolith); the planned messaging step keeps the same shape. Listed so it can be
defended, not fixed.

## Not in this change

Archived-group checks (lifecycle feature), notifications for leaving, an activity feed.
