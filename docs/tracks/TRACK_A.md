# Track A: account + group

**Branch:** `track/a-account-group` · **Tasks:** plan **A1, A2, A3** · **SYNC section:** Track A

**You own (only touch these):** `<pkg>/account/**` (except `account/api` and `package-info.java`), `<pkg>/group/**` (same exceptions), `db/migration/account/**`, `db/migration/group/**`, `db/migration-dev/**`, and your tests under `<test>/account/**` and `<test>/group/**`.

**You deliver:**
- `UserDirectory` and `CurrentUser` implementations, the stub identity, `GET /me`, `GET /users`.
- Groups: `POST /groups`, `GET /groups`, `GET /groups/{groupId}`.
- Invitations: `POST /groups/{groupId}/invitations`, `POST /invitations/{token}/accept`.
- The real `GroupMembership` bean, plus events `GroupCreated` and `MemberJoined`.

**Notes**
- Match `docs/api/openapi.yaml` exactly (field names, status codes, error `code`s). Rules: invitations are the only way to join (R9, valid 7 days) and the status table in spec §7.
- `DevIdentityFilter` is `@Profile("dev")`. Your tests use `@ActiveProfiles("dev")` so the seeded users exist. `GET /users` needs no header; every other endpoint answers 401 `UNAUTHENTICATED` without a known `X-User-Id`. Skip the filter for `/actuator/**`.
- The seed migration `db/migration-dev/V190__dev_users.sql` inserts the four dev users with the fixed ids from the README.
- Creating a group publishes `GroupCreated` and `MemberJoined(ADMIN)` in one transaction; accepting an invitation publishes `MemberJoined(MEMBER)`.
- Expiry: inject a `java.time.Clock` bean so the test can move time. Tokens: at least 128 bits of randomness, URL-safe.
- Group display names of members come from `UserDirectory`, never copied into group tables.
- Currency is stored as `EUR`. Group name: non-blank, max 100 characters; violations are 400 `BAD_REQUEST`.

**Done checklist**
- [ ] A1, A2, A3 tests all green, `./mvnw -q verify` green (including `ModularityTests`).
- [ ] Responses match the contract (check by hand against Prism examples or the controller tests).
- [ ] Done report written in the SYNC file.

**Prompt to paste into the session** (open Claude Code in `C:/Users/Saturn/Desktop/Studium/Master/ws_26_27/sharewise-track-a`):

> You are Track A (account + group) of the ShareWise Ex4 build. Read `docs/tracks/README.md` and `docs/tracks/TRACK_A.md`, then execute plan tasks A1–A3 from `docs/2026-10-24-sharewise-modulith-ex4.md` with test-driven development. Follow the SYNC protocol in the README. Do not merge or push. Stop when the done checklist is complete and tell me what to review.
