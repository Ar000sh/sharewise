# Parallel tracks: common rules (read this first)

You are one of four parallel track sessions building the ShareWise Ex4 slice. A separate **hub** session did Phase 0 (this foundation), owns the API contract and the module interfaces, and does the final integration. You work only on your track.

**Read, in this order:** this file → your `docs/tracks/TRACK_<X>.md` → your tasks in `docs/2026-10-24-sharewise-modulith-ex4.md` (the plan) → the parts of `docs/2026-10-24-sharewise-modulith-ex4-design.md` (the spec) they refer to. The plan's `<pkg>` is `backend/src/main/java/de/hsh/favs/sharewise`, `<test>` is `backend/src/test/java/de/hsh/favs/sharewise`.

## Rules

1. **Touch only your own directories** (listed in your track file). If you need something outside them, ask in the SYNC file.
2. **Never edit** `docs/api/openapi.yaml`, any `*/api/*` package, `shared/**`, `package-info.java` files, `application*.yml` or `pom.xml`/`package.json` dependencies without asking. Request the change in the SYNC file under *Contract change requests*; the hub makes it, announces it in the SYNC file, and you rebase onto `main` (`git rebase main`; all worktrees share one repository, no fetch needed).
3. **TDD:** write the failing test, watch it fail, implement, watch it pass, commit. One commit per plan task at least; keep the build green at every commit.
4. **Do not merge** and do not push. When your done checklist is complete, write the done report and stop. The hub and the user review the branch.
5. Keep to the plan's names, signatures and values. If the plan is wrong or ambiguous, make the smallest decision that satisfies the spec, and write it in the SYNC file under *Rulings*.
6. Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## SYNC file

`C:/Users/Saturn/Desktop/Studium/Master/ws_26_27/FAVS/work/sharewise_SYNC.md` (outside all worktrees). **Write only in your own track section** and in no one else's. Update it when you start, when a task is done, when you are blocked, and at the end. Read the *Hub* section at the start of each work block for announcements.

Done report (in your section, when finished): branch name, last commit hash, the exact commands you ran with their pass/fail counts, any deviations from the plan, anything the hub must know for integration.

## Facts about Phase 0 (already decided, do not redo)

- Backend: Spring Boot **4.1.2**, Java 21, Spring Modulith **2.1.1** (JPA event publication, table created by `db/migration/shared/V001`). Maven only via `./mvnw` in `backend/` (Maven is not on the PATH). **Docker Desktop must be running** for any backend test.
- Tests that need a database import the generated container config: `@Import(de.hsh.favs.sharewise.TestcontainersConfiguration.class)` (public, Postgres 17). This replaces the plan's `support/PostgresTestConfig`.
- Module boundaries are checked by `ModularityTests`. Each module's `package-info.java` lists its allowed dependencies: `account → shared`; `group → account::api, shared`; `expense → account::api, group::api, shared`; `settlement → account::api, group::api, expense::api, shared`. Other modules may only use another module's `api` package. Keep everything else package-private.
- Flyway: one folder per module under `backend/src/main/resources/db/migration/<module>/`, versions prefixed per module (account `V1xx`, group `V2xx`, expense `V3xx`, settlement `V4xx`). Dev seed data: `db/migration-dev/` (only loaded with profile `dev`). `ddl-auto` is `validate`, so every entity needs its migration.
- `shared`: `GroupId`, `MemberId`, `UserId` (records of `UUID value`), `Money.eur(long)`, `ErrorCode`, `DomainException`, and `ProblemExceptionHandler` (already maps `DomainException` and malformed input to problem+json). `MemberId` of a user is `new MemberId(userId.value())`.
- Cross-module interfaces and events exist in each `*/api` package (see plan Task 0.4). `GroupMembership.requireMember(group, member)` implements the caller check; use it.
- Frontend: Next.js **16.4.3**, App Router with `src/`, Tailwind 4, Vitest 5 + Testing Library (jsdom). The eight `app/api/**/route.ts` proxies, `src/lib/backend.ts`, `src/lib/api.ts` (`api<T>(path)`, `ApiError`), and the generated `src/api/schema.d.ts` already exist. Proxy-generated errors (backend down) use code `BACKEND_UNREACHABLE`, which is not in the contract enum.
- Dev users (fixed ids): Alice `aaaaaaaa-0000-0000-0000-000000000001`, Bob `…0002`, Carol `…0003`, David `…0004`.

## Commands

```
backend:   cd backend && ./mvnw -q verify           (all tests; add -Dtest=ClassName for one)
frontend:  cd frontend && npm run lint && npm run typecheck && npm test && npm run build
mock API:  sh docs/api/mock.sh                        (Prism on :4010, serves the contract examples)
frontend against the mock:  BACKEND_URL=http://localhost:4010 npm run dev
```
First time in a new worktree: `cd frontend && npm install`. Maven downloads its dependencies on the first backend run.
