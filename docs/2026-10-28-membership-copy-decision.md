# Decision: Expense keeps its own copy of group membership (28.10.26)

**Status:** decided by the team member on 28.10 and implemented on `feature/expense-membership-copy`. It **replaces D-A** of `2026-10-27-diagram-alignment-design.md` (the synchronous snapshot).
**Diagram:** this is what `work/ShareWise_Domain_Model.drawio` already draws (`GroupMembershipView` "local copy updated from group events"). Delta #12 in `diagram-deltas.md` is therefore closed.

## The problem

`Expense.record` must enforce R2, R3 and R5: the payer and every participant are active members of the group, and the group is open. An expense only holds a `GroupId` (aggregate rule 3), so it cannot look this up itself. Somebody has to tell it who is in the group.

Example (Mallorca): Alice created the group, Bob has just accepted the invitation. Bob immediately adds "Dinner, 180". Does the expense context already know that Bob is a member?

## What we chose

The expense context owns two small tables and fills them from the group context's events. It never calls the group module.

| Piece | Where | Job |
|---|---|---|
| `GroupMembershipView` | `expense.domain` | what `Expense.record` needs: `isOpen`, `activeMembers`, `admins`; the diagram's name, now owned by Expense |
| `expense_known_groups`, `expense_known_members` | migration V303 | the copy: one row per group, one per (group, user); `as_of` = `occurredAt` of the last event applied |
| `MembershipCopyListener` | `expense.application` | `GroupCreated` and `MemberJoined` write the copy (asynchronous, after the group's commit) |
| `MembershipCopyRepository` | `expense.persistence` | upserts that only apply an event at least as new as the stored one; reads the view |
| `KnownMemberships` | `expense.application` | reads the view and **waits a short, bounded time** for the people a call needs (the retry below) |

The group module lost its `view()` method and `Group.membershipView()`; the `GroupMembership` port still serves the other callers (settlement's `Balances`, the group controllers). `ConventionTests.expenseDoesNotCallTheGroupPort` keeps expense from depending on the port again.

### The retry

The copy is filled after the group's transaction commits, so it can be a few milliseconds behind. `ExpenseService` asks `KnownMemberships.awaiting(group, caller + payer + participants)`:

1. Read the copy. If all of those people are active members, go on.
2. Otherwise sleep 100 ms and read again, up to 6 reads (about 0.5 s). Configurable: `sharewise.expense.membership-wait.attempts` and `.pause`.
3. After the last read, hand over what is known. The normal rules then refuse (`GROUP_NOT_FOUND`, `NOT_A_MEMBER`, `PAYER_NOT_IN_GROUP`, `PARTICIPANT_NOT_IN_GROUP`).

The wait happens **before** the transaction. The save and the publish run in a `TransactionOperations` block afterwards, so no database connection is held while sleeping.

### What an event does to the copy

Idempotent and order-tolerant by design (see `2026-10-27-event-delivery-and-messaging.md`, points 1 and 3):

- applying the same event twice changes nothing;
- an older event arriving after a newer one is ignored (`as_of` guard);
- `MemberJoined` before `GroupCreated` is stored, but the group stays "unknown" (and waits) until its own event arrives.

## The alternative we rejected for now: a synchronous port

The branch `alt/membership-port-snapshot` keeps this variant, runnable and green, for comparison (`git diff alt/membership-port-snapshot main`).

Before 28.10 (decision D-A) `Expense.record` received a `GroupMembershipView` that `GroupMembership.view(group)` built from a fresh database read when the expense was recorded.

## What the copy bought us

- **No call from Expense to Group.** Expense depends only on the group's *events* (a published contract), not on its port. When the services are split in P3 nothing changes in the expense domain; the HTTP call per expense and the availability coupling (Group down means no expenses) never exist.
- **It is what the diagram draws.** The professor-facing model and the code agree; delta #12 is closed.
- **A rehearsal for messaging.** The listener is idempotent and tolerates disorder, exactly what RabbitMQ will demand (event-delivery doc, points 1 to 3, 7). The same code later consumes a queue instead of an in-process event.
- **A natural place for the next rules.** Close, reopen, archive, leave, remove and promote events (Ex5) simply update the same two tables.

## What it costs

- **Eventual consistency becomes visible.** A just-joined member may not be known yet. The bounded wait hides this in normal cases (milliseconds), but it is a mitigation, not a guarantee.
- **A real non-member pays the whole wait** (about 0.5 s) before being refused with 403 or 422. Acceptable: it is the failure path.
- **A slow or stuck listener turns into wrong answers.** If the `MemberJoined` handler fails (and is redelivered only on restart, see the event-delivery doc), the user is told "not a member" although they are. The message cannot tell "never a member" from "not known yet". A broker with retries and a dead-letter queue (P3) is the real cure; until then this is a known weakness.
- **Staleness in the other direction.** Once members can leave or the group can close (Ex5), the copy may say "active" for a few milliseconds after the group said otherwise. A removed member could record one more expense in that window. The group status and membership changes of Ex5 must be applied with the same `as_of` guard.
- **More code and a second source of truth.** Two tables, a listener, a repository, a wait, and their tests; the data exists twice. A bug in the listener means the copy drifts from the group.
- **Backfill.** Migration V303 copies existing groups and memberships once with SQL that reads the group tables. That is the only place the expense module touches them, and only in a one-off migration.
- **Still open:** correcting an expense after a participant left (the copy says inactive, R3 refuses). Rule candidate from the review: allow unchanged former participants, forbid adding new ones. Decide with the Ex5 corrections.
- **Currency** is not in the copy yet, so Expense cannot check "one currency per group" (review D12). Add it with the first real second currency.

## Not done on purpose

- No client-side retry: the server-side wait is enough and keeps the API unchanged.
- No synchronous fallback to the group port when the copy is incomplete (the "hybrid" option): it would bring the call back and hide the copy's gaps.
- Settlement and the group controllers still use the `GroupMembership` port (`requireMember`, `Balances`). Settlement gets its own copy only if it is extracted.
