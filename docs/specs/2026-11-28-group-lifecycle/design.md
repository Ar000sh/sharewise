# Group lifecycle: close for settlement, reopen, archive (J12, J13)

Date: 28.11.26. Rules: L1 to L6, R5. Diagram operations: `Group.closeForSettlement`, `Group.reopenGroup`,
`Group.archiveGroup`; events `GroupClosedForSettlement`, `GroupReopened`, `GroupArchived`; `AllBalancesSettled` (already
published by settlement) finally gets its consumer. Builds on `feature/membership-exit`.

```
          closeForSettlement()               AllBalancesSettled (auto)
  OPEN ───────────────────────► CLOSED_FOR_SETTLEMENT ───────────────────► ARCHIVED (final, read-only)
    ▲                                 │                  or archiveGroup() by an admin, all balances 0
    └──────── reopenGroup() ──────────┘
```

## What is built

| Piece | Change |
|---|---|
| `Group.closeForSettlement(admin, now)` | OPEN → CLOSED_FOR_SETTLEMENT; returns `GroupClosedForSettlement` |
| `Group.reopenGroup(admin, now)` | CLOSED_FOR_SETTLEMENT → OPEN; returns `GroupReopened` |
| `Group.archiveGroup(admin, now)` | CLOSED_FOR_SETTLEMENT → ARCHIVED when every active member's balance flag is zero; returns `GroupArchived` |
| `Group.archiveWhenAllBalancesSettled(now)` | the automatic path (L5): archives a closed group, ignores any other status; returns the event or nothing |
| Status guards | invite and accept need OPEN (J3); promote, leave and remove are refused once ARCHIVED (L6, M4) |
| Listener | `GroupListener.on(AllBalancesSettled)` → `GroupService.archiveWhenAllBalancesSettled` |
| HTTP | `POST /groups/{groupId}/close`, `/reopen`, `/archive` |
| Notification | `GROUP_CLOSED`, `GROUP_REOPENED`, `GROUP_ARCHIVED` for every active member except the admin who did it |
| Frontend | the parked banner of the journey design (U11, board B-14), admin buttons with a confirmation dialog, no "Add expense" or invite form unless OPEN |

## Decisions

| # | Decision | Why / concept | Who |
|---|---|---|---|
| G1 | A group whose balances are already 0 when it is closed **stays closed**. It is archived when `AllBalancesSettled` arrives or when an admin archives it by hand. | `AllBalancesSettled` only fires on a change from "somebody owes" to "nobody owes". Archiving is final (L6), so it must not run on a flag copy that may still miss a booking in flight. | user, 25.11 |
| G2 | Automatic archiving only acts on a CLOSED_FOR_SETTLEMENT group. An OPEN group whose balances happen to reach 0 mid-trip stays open. | Answers the open question "auto-archive scope" of the requirements (§8): the diagram draws auto-archive only from CLOSED. | follows the diagram |
| G3 | Manual archive checks the group's balance flags (`Membership.hasZeroBalance`) of the active members; former members are zero by R11. Refused with `BALANCE_NOT_ZERO`. | Same event-fed local copy as leaving (D12); no synchronous call to settlement, which would make the module graph cyclic. | rule L4, L5 |
| G4 | New codes: `GROUP_NOT_CLOSED` (409) for reopen or archive of a group that is not closed; `GROUP_ARCHIVED` (409) for any change to an archived group. Closing a group that is not OPEN reuses `GROUP_NOT_OPEN`. | One code per reason the user can act on: "reopen first" is different from "this group is history". | (call) |
| G5 | Every member except the acting admin gets an inbox entry. The automatic archive has no acting person: its event carries `archivedBy = null`, so every active member gets an entry without an actor ("Mallorca 2027 was archived: everybody is settled"). `notifications.actor_id` becomes nullable (migration `V502`). | J12 step 5, J12b step 3, J13; the inbox already shows "Someone" for a missing actor. | user, 28.11 |
| G6 | Recipients are the active members when the event is processed, read through the group port `GroupMembership.view`. | The event stays small (ids only); the notification module already depends on `group::api`. | (call) |
| G7 | Settlement payments stay allowed while CLOSED (J8 precondition). Nothing new is needed in settlement: `SettlementPaymentService` already refuses a payment or waiver in an ARCHIVED group (`GROUP_NOT_OPEN`, "The group is archived") and allows every other status. | L2, J8 | rule, exists |

## Error order

- `closeForSettlement`: admin (`NOT_A_MEMBER`, `NOT_AN_ADMIN`), not archived (`GROUP_ARCHIVED`), OPEN (`GROUP_NOT_OPEN`).
- `reopenGroup`: admin, not archived (`GROUP_ARCHIVED`), CLOSED_FOR_SETTLEMENT (`GROUP_NOT_CLOSED`).
- `archiveGroup`: admin, not archived (`GROUP_ARCHIVED`), CLOSED_FOR_SETTLEMENT (`GROUP_NOT_CLOSED`), every active
  member's flag zero (`BALANCE_NOT_ZERO`).
- `inviteMember`, `acceptInvitation`: OPEN (`GROUP_NOT_OPEN`) after the checks they had before.
- `promoteToAdmin`, `leaveGroup`, `removeMember`: not archived (`GROUP_ARCHIVED`) right after the caller check.

All of them lock the group row, so an admin closing and a member joining at the same moment are serialized.

## Not in this change

A summary page before closing (J12 step 2 shows "total spent, final balances, plan": the Balances and Settle up tabs
already show them, the dialog points there), "Archived groups" as a separate list (the group list shows the status
badge), export (Could).
