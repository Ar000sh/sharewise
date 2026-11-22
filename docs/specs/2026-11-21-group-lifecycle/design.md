# Group closure, archive versions, and corrections

## Agreed behavior

Only admins close an OPEN group and only at zero balances. A successful close automatically persists an immutable archive and changes the group to CLOSED. Reopening requires an admin, a reason, and the current lifecycle revision. It preserves historical versions and confirmed payments. Another close creates another version. Reaching zero alone never closes a group.

The full reasoning is in [the business analysis](../../notes/2026-11-20-group-lifecycle-business-analysis.md).

## Consistency boundary

Every financial command first obtains the group row's write lock through `GroupMembership.lockOpen`, in the command's existing transaction. Group membership writes and lifecycle commands use the same row lock. Payment commands and closure then acquire the ledger lock. Asynchronous ledger listeners acquire only the ledger lock; balance projection listeners take the group lock after the originating transaction commits. This consistent lock order avoids a group/ledger inversion.

Closure checks every persisted expense reference, correction reversal reference, and confirmed/waived payment reference against the booked ledger. Pending payments and incomplete booking refuse closure. Once all references are present, the ledger balance includes every accepted financial operation. The group lock prevents a new financial command from crossing the boundary during validation and snapshot persistence.

This check is intentionally explicit rather than a timer or an eventually consistent `hasZeroBalance` flag. Future financial operation types must participate in both the write lock and expected-reference check.

## Modules and ports

`GroupFinancialHistory` is a read port owned by Group and implemented in Settlement using its own repositories and the Expense module's `ExpenseHistory` read port. This is dependency inversion: Group depends on its own abstraction, not Settlement internals. The compile-time module graph remains acyclic. Both ports return plain snapshot records.

`GroupMembership.lockOpen` is a narrow exception to ports being transaction-independent reads: it requires an existing write transaction and holds a lock until that transaction ends. The domain still sees only plain membership values.

## Immutable history

Group owns `GroupArchive` and `GroupLifecycleEntry` child entities. Each close stores a JSON snapshot in TEXT within the same transaction as the status change and lifecycle entry. Serialization at this persistence boundary uses JsonMapper in the application service; controllers do not supply snapshot data. Snapshot fields are not updatable. There is no archive editing/deletion API. Archive version is unique per group.

Snapshots preserve member names, expenses and original replaced records, shares, payment/waiver history, zero balances, and closure metadata. Viewing is restricted to active members, matching current group read authorization. A PDF/export is not required for this feature.

Close/reopen requests carry `expectedRevision`. Each accepted transition increments it. A duplicate or stale request gets LIFECYCLE_CONFLICT and cannot create an extra archive or act on a later reopen cycle.

## Corrections

An authorized recorder, payer or admin may correct an active equal-split expense's title and amount while OPEN. Payer and participants remain unchanged. The old expense stays as CORRECTED; a replacement expense stores `correctedFrom`, and the original stores a unique ledger reversal reference. Ledger reversal plus replacement are booked atomically from one ExpenseCorrected event and leave confirmed payments intact. Correction notifications identify the actual editor. Other correction dimensions and expense removal are outside this implementation.

## Membership default

All existing group membership writes require OPEN. Admins can reopen before inviting/promoting. Reopening does not bypass existing authorization. This selects the documented proposed default; it adds no special rights for ordinary participants.

## Migration and failure handling

V207 adds archive/history tables and lifecycle revision. It refuses unexpected legacy CLOSED_FOR_SETTLEMENT or ARCHIVED rows for explicit review rather than automatically reopening them or inventing historical snapshots. These statuses previously had no producing operation. V303 adds correction references. Existing migrations are untouched.

Failure to validate, serialize or persist rolls back the closure transaction. Archive versions are created before success is returned. Notifications use the lifecycle entry UUID as their idempotent reference.

## API and UI

GET lifecycle/archives and POST close/reopen live under `/groups/{groupId}`. The lifecycle UI shows the warning before closure, requires a reopening reason, displays historical versions and decision history, and refreshes group queries. Closed screens hide financial and membership write controls. The backend independently refuses writes from stale clients.

The Settle up tab remains a payment feature, not a group status. Closed groups expose payment history in archives. There is no automatic close consumer of AllBalancesSettled.
