# Variant: event-fed membership copy

This branch is the P3-ready alternative to `main` for one decision: how Expense learns who is in a group.

- **`main`:** `Expense.record` receives a `GroupMembershipView` that the group module's `GroupMembership` port builds with a fresh query on every call (D-A). No copy, no wait.
- **This branch:** Expense keeps its own copy fed by `GroupCreated` and `MemberJoined`, with a short bounded wait. See `2026-10-28-membership-copy-decision.md` for alternative, gains and costs.

Compare with `git diff main feature/expense-membership-copy -- backend`.
