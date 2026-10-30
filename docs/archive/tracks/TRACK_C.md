# Track C: settlement

**Branch:** `track/c-settlement` · **Tasks:** plan **C1, C2** · **SYNC section:** Track C

**You own (only touch these):** `<pkg>/settlement/**` (except `settlement/api` and `package-info.java`), `db/migration/settlement/**`, and your tests under `<test>/settlement/**`.

**You deliver:** the balance read model updated from `ExpenseRecorded`, the `Balances` implementation, and `GET /groups/{groupId}/balances`.

**Notes**
- Hexagonal: a listener (driving adapter) calls an application service that updates balances through a port; the query side merges stored balances with the member list.
- **You cannot use the other tracks' code.** In tests, publish a hand-built `ExpenseRecorded` (with Modulith's `Scenario` API) and supply fakes for `GroupMembership`, `UserDirectory` and `CurrentUser` as test beans.
- Update rule (R14): payer credited `totalCents`, every share holder debited their `shareCents`; a payer who is also a participant nets out. The sum over a group is always 0 (property test).
- The listener is `@ApplicationModuleListener` (asynchronous, after commit). It must be **idempotent per `expenseId`** (a redelivered event must not double count). Store the processed expense ids in your own table.
- `GET /balances` returns **every current member** (from `GroupMembership.members`), with 0 for members without stored balance, display names from `UserDirectory`, never stored. Caller check with `requireMember`. Order: stable (by display name).
- No foreign keys to other modules' tables.

**Done checklist**
- [ ] C1, C2 tests all green, `./mvnw -q verify` green (including `ModularityTests`).
- [ ] Responses match the contract (`Balance[]`, sum 0).
- [ ] Done report written in the SYNC file.

**Prompt to paste into the session** (open Claude Code in `C:/Users/Saturn/Desktop/Studium/Master/ws_26_27/sharewise-track-c`):

> You are Track C (settlement) of the ShareWise Ex4 build. Read `docs/tracks/README.md` and `docs/tracks/TRACK_C.md`, then execute plan tasks C1–C2 from `docs/2026-10-24-sharewise-modulith-ex4.md` with test-driven development. Follow the SYNC protocol in the README. Do not merge or push. Stop when the done checklist is complete and tell me what to review.
