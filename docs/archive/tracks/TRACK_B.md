# Track B: expense

**Branch:** `track/b-expense` · **Tasks:** plan **B1, B2, B3** · **SYNC section:** Track B

**You own (only touch these):** `<pkg>/expense/**` (except `expense/api` and `package-info.java`), `db/migration/expense/**`, and your tests under `<test>/expense/**`.

**You deliver:** the Expense aggregate with equal split (R1–R4), the record-expense use case with persistence, `ExpenseRecorded`, and `POST /groups/{groupId}/expenses`, `GET /groups/{groupId}/expenses`.

**Notes**
- Hexagonal: `domain` (pure Java, no Spring), `application` (use cases and ports), `adapter/persistence`, `adapter/web`. Domain tests need no Spring context.
- **You cannot use Track A's code.** In tests supply a `FakeGroupMembership` (implements `GroupMembership`, keep the default `requireMember` so it behaves like the real one) and a fake `CurrentUser` (Alice by default) as test beans. Real beans arrive at integration.
- The use case checks, in this order: caller is a member (`requireMember`, 404/403), group status is OPEN (422 `GROUP_NOT_OPEN`), payer is a member (422 `PAYER_NOT_IN_GROUP`), every participant is a member (422 `PARTICIPANT_NOT_IN_GROUP`), then domain rules (amount > 0 → 422 `AMOUNT_NOT_POSITIVE`; at least one participant, no duplicates → 422 `NO_PARTICIPANTS`). `split` other than `EQUAL` → 422 `SPLIT_NOT_SUPPORTED`.
- Rounding and share order exactly as in the Global Constraints (remainder cents to the first participants in the given order). Property test: shares always sum to the total.
- Publish `ExpenseRecorded` inside the same transaction as the save. No foreign keys to other modules' tables; store `group_id`, `payer_id`, member ids as plain UUIDs.
- `GET` returns newest first (by date, then creation time) with the shares.

**Done checklist**
- [ ] B1, B2, B3 tests all green, `./mvnw -q verify` green (including `ModularityTests`).
- [ ] Responses match the contract.
- [ ] Done report written in the SYNC file.

**Prompt to paste into the session** (open Claude Code in `C:/Users/Saturn/Desktop/Studium/Master/ws_26_27/sharewise-track-b`):

> You are Track B (expense) of the ShareWise Ex4 build. Read `docs/tracks/README.md` and `docs/tracks/TRACK_B.md`, then execute plan tasks B1–B3 from `docs/2026-10-24-sharewise-modulith-ex4.md` with test-driven development. Follow the SYNC protocol in the README. Do not merge or push. Stop when the done checklist is complete and tell me what to review.
