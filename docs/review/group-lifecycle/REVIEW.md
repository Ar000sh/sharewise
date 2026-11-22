# Group lifecycle review

The feature replaces the unused settlement/archived states with OPEN/CLOSED. Admin closure requires zero balances and completed financial processing, creates an immutable archive in the same transaction, and notifies members. Admin reopening requires a reason. Reclosure creates a new archive, and historical snapshots remain unchanged.

Start with `docs/specs/2026-11-21-group-lifecycle/design.md`. Review `GroupArchiveService`, `GroupFinancialHistoryImpl`, and `GroupMembership.lockOpen` together: they establish the closure boundary across financial writes and asynchronous ledger bookings.

Review the group lifecycle domain and integration tests for the full correction/repayment sequence, permission failures, stale requests and concurrent closure. The UI includes warning/confirmation, reason input, archive inspection and a title/amount correction form. All new routes use the existing authenticated proxy.

Limits: active members can view archives; export and participant/payer changes are not included. Membership writes require reopening. Legacy non-OPEN statuses require explicit review before migration. No deployment or push is part of this change.

Validation completed on 22 November 2026:

- Full backend verification: 428 tests, zero failures/errors; final focused rerun also passed after correction and history-order refinements.
- Frontend: lint, typecheck, production build and all 246 tests across 29 files passed using Node 24. The tests emit a jsdom navigation warning.
- OpenAPI lint and type generation passed; existing contract lint warnings remain.
- Implementation remains in the working tree for review, with contract-only commits on the feature branch. Existing unrelated user edits were preserved.
