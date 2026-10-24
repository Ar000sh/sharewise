# ShareWise Modulith: Ex4 Design (due Mon 26.10.26), with an outlook on Ex5 (02.11.26)

**Status:** draft for team review. Items marked 🟢 are decisions from the conversation with the user. Items marked **(call)** are my own calls, to be checked.
**Domain source of truth:** `work/ShareWise_Requirements.md` (rules R1–R17, L1–L6, journeys J1–J17). If this file disagrees with it, the requirements file wins for domain rules; this file wins for implementation scope.
**Related:** `sharewise/docs/01_Requirements_from_Prof.md` (what the prof demands), `sharewise/docs/02_Reference_Template_Takeaways.md` (demo repo analysis, proposals T1–T7).

## 1. Intent

Build the ShareWise **modular monolith** (Spring Boot backend + Next.js frontend). Ex4 and Ex5 are the build window. Everything from Ex6 on (containers, pipelines, deployment, service extraction) packages and splits what we build now, so the module boundaries must be **real and checkable**, not just folders. P2 (17.11) presents the implementation, its limits and the change drivers, so the code should make those arguments demonstrable.

**Ex4 deliverable, due Mon 26.10:** a running backend and frontend with a first vertical slice working end to end: **create group → invite and join → record an equal-split expense → see balances.** The slice includes one real cross-module event flow.

The prof gives no minimum scope for Ex4. The prof explains the layered demo in class on 26.10. This design is the realistic target for two days, not a guess at the prof's grading.

## 2. Goals, non-goals, anti-goals

**Goals**
- A slice that runs end to end through all four modules and both apps.
- Module boundaries enforced by the build (a violating change fails a test).
- **The work splits into tracks that several sessions or agents can build in parallel** (§8). This is a design requirement, not a nicety.
- Reproducible local setup: one command for the database, one for each app.

**Non-goals for Ex4** (planned for Ex5 unless noted)
- Exact and percentage splits; correcting and removing expenses.
- Leave, remove, promote; invitation decline and expiry handling; close, reopen, archive.
- Settlement payments, confirmation, the simplified settlement plan.
- Keycloak. Notifications. Activity feed. Anything from Could or Won't in the requirements.
- Containers for the apps (Ex6), pipelines (Ex7). Only the database runs in Docker now.

**Anti-goals** (a failure even if it technically works)
- Modules that read each other's tables or import each other's internals.
- A frontend that calls Spring directly or codes against a backend that does not match the contract.
- Floating-point money.
- A demo that only works with hand-edited database rows.

## 3. Decisions

| # | Decision |
|---|---|
| D1 🟢 | Keycloak is **deferred**. Ex4 uses a stub identity (§6). Keycloak is a possible Ex5 add-on, depending on time. |
| D2 🟢 | **Postgres** in Docker (compose) for development, **Testcontainers** for backend tests. No H2. Schema managed with **Flyway**. |
| D3 🟢 | The Ex4 slice is the one in §1. The remaining features move to Ex5 (§9). |
| D4 🟢 | Modules = bounded contexts: `account`, `group`, `expense`, `settlement`, plus a `shared` package (not a context). Hexagonal in `expense` and `settlement`, layered in `group` and `account`. (From P1 and proposals T2–T4.) |
| D5 🟢 | **Spring Modulith** enforces boundaries (`ApplicationModules.verify()` runs in the test suite). |
| D6 🟢 | Browser → Next.js `/api/...` route handlers → Spring Boot at `BACKEND_URL`. One shared proxy helper. Records as DTOs; domain objects are never exposed. |
| D7 **(call)** | **Contract first.** `sharewise/docs/api/openapi.yaml` is the single interface between the backend and frontend tracks (§7). |
| D8 **(call)** | Money is an integer in **minor units** (cents) everywhere: API field `amountCents`, domain `Money(long cents, Currency)`. Group currency is fixed to **EUR** in Ex4. |
| D9 **(call)** | IDs are UUIDs. `MemberId` is its own type in `shared` but carries the same UUID value as the user's `UserId` in Ex4. This resolves the open UserId vs MemberId question for now; revisit if Keycloak changes it. |
| D10 **(call)** | "Payer and participants belong to the group" (R2/R3) is checked in the Expense application service through a port `GroupMembership` that Group exposes as public API. Expense does not keep a membership copy. This answers the open question in requirements §8. |
| D11 **(call)** | Events between modules are plain Java records published with Spring's `ApplicationEventPublisher` and consumed with Spring Modulith's `@ApplicationModuleListener` (async, after commit, persisted in the event publication registry). Balances are therefore **eventually consistent**, which is also P2 material. |
| D12 **(call)** | Java 21 (installed), Maven wrapper from start.spring.io, newest stable Spring Boot and the matching Spring Modulith version. Exact versions are taken at scaffold time from start.spring.io and checked against Context7 docs. Root package `de.hsh.favs.sharewise` (confirm with the team). |
| D13 **(call)** | Ex4 includes a minimal invitation flow (create a link token, accept it) because a one-member group makes balances meaningless. Decline and email are Ex5. The 7-day expiry (R9) is stored and **enforced at accept time**: an expired token returns 410 `INVITATION_EXPIRED`. |
| D14 **(call)** | **Display names** are owned by `account`. `group` and `settlement` read them synchronously through `UserDirectory`; nothing stores names outside `account`, and events carry ids only. Declared Modulith dependencies: `group → account`, `settlement → account`. |
| D15 **(call)** | `GET /balances` returns **every current member of the group**, including those with no expenses (balance 0). Settlement stores balances only for members who appear in expenses and merges the member list from `GroupMembership.members` at read time. Declared dependency: `settlement → group`. Settlement does not listen to `MemberJoined` in Ex4. |
| D16 **(call)** | The caller-is-member check (403 `NOT_A_MEMBER`) is done in each module's application service through `GroupMembership.isMember`, for every group-scoped endpoint including reads. The fake used in Track B tests and the real implementation from Track A must behave the same: a non-member caller is rejected, an unknown group is 404. |

## 4. Module design

```
de.hsh.favs.sharewise
 ├─ shared      Money, Currency, GroupId, MemberId, UserId, error types (public)
 ├─ account     users (generic)             layered
 ├─ group       group, membership, invitation  layered
 ├─ expense     expense aggregate           hexagonal
 └─ settlement  balances (read model now, payments in Ex5)   hexagonal
```

Each module has a **public API package** (interfaces, command and event records) and everything else package-private. The API packages are created in Phase 0 (§8) so the tracks can code against them before the implementations exist.

| Module | Public API | Owns (tables) | Ex4 behaviour |
|---|---|---|---|
| `account` | `CurrentUser` (resolves the caller's `UserId` and display name), `UserDirectory.find(UserId)` | `users` | Seeded dev users (Alice, Bob, Carol, David). Reads the stub identity header. |
| `group` | `GroupMembership.isMember(GroupId, MemberId)`, `GroupMembership.members(GroupId)`; events `GroupCreated`, `MemberJoined` | `groups`, `members`, `invitations` | Create group (creator becomes ADMIN member, status OPEN). Create and accept an invitation. List and read groups. |
| `expense` | events `ExpenseRecorded` (see below) | `expenses`, `expense_shares` | Record an equal-split expense (R1–R4, R6 not needed yet); list a group's expenses. Rejects when the group is not OPEN (always OPEN in Ex4). |
| `settlement` | `Balances.of(GroupId)` | `balances` (read model) | Listens to `ExpenseRecorded`, updates per-member balances (R14). Serves the balances query. |

`ExpenseRecorded` carries everything Settlement needs, so Settlement never calls Expense: `expenseId, groupId, payerId, totalCents, shares: [{memberId, shareCents}]`.

**Rounding (R4):** `total ÷ n` with the remainder cents distributed one each to the first participants in the order given. The sum of the shares always equals the total.

**Balance rule (R14):** payer is credited the total, each participant is debited their share. The sum over a group's balances is always 0. A property test checks this.

## 5. Data and persistence

- One Postgres database, one schema, but **each module owns its own tables with a module prefix** and no foreign keys across modules (IDs only). This keeps extraction in Ex9 possible. Flyway migrations are split per module (`db/migration/<module>/V…`).
- The Spring Modulith event publication table (`event_publication`) is created by Flyway too.
- Dev database: `docker compose up db` (compose file in `sharewise/`). Tests: Testcontainers, no manual setup. Docker Desktop must be running.

## 6. Identity (stub)

- Frontend has a **dev user switcher** (Alice, Bob, Carol, David). The selection is stored in the browser and sent to the Next.js route handlers, which forward it to the backend as the header `X-User-Id`.
- Backend: a filter resolves `X-User-Id` into the `CurrentUser` port. Missing or unknown id → 401 problem response. The header is **never trusted outside dev**: the filter is only registered under the `dev` profile, and the production wiring point is the same port, so Keycloak later replaces the filter with JWT validation (JWT subject → `UserId`) without touching any other module.
- The seeded users come from a dev-profile Flyway migration or runner.

## 7. API contract (the parallelism hinge)

`sharewise/docs/api/openapi.yaml` (OpenAPI 3.1) is written in Phase 0 and is the only coupling between the backend and frontend tracks.

**Ex4 endpoints.** The paths below are the backend's. The browser calls them under `/api` on Next.js, and the proxy forwards `/api/x` to `BACKEND_URL/x`:

| Method and path | Purpose | Notes |
|---|---|---|
| `GET /me` | current user | `{userId, displayName}` |
| `GET /users` | dev: list seeded users | dev profile only; powers the user switcher |
| `POST /groups` | create group | body `{name}`; 201 with the group; caller becomes ADMIN |
| `GET /groups` | my groups | list of `{id, name, status, memberCount}` |
| `GET /groups/{groupId}` | group detail | includes `members: [{memberId, displayName, role}]` |
| `POST /groups/{groupId}/invitations` | create an invite | returns `{token, expiresAt}`; caller must be a member |
| `POST /invitations/{token}/accept` | join | returns the group; 404 unknown token, 409 already a member, 410 expired |
| `POST /groups/{groupId}/expenses` | record expense | body `{title, amountCents, date, payerId, participantIds, split: "EQUAL"}`; other split values → 422 `SPLIT_NOT_SUPPORTED` in Ex4 |
| `GET /groups/{groupId}/expenses` | list expenses | newest first, each with its shares |
| `GET /groups/{groupId}/balances` | balances | `[{memberId, displayName, balanceCents}]`; sum is 0 |

**Errors:** RFC 7807 `application/problem+json` with a stable `code`. One status mapping for all endpoints:

| Status | When | Codes |
|---|---|---|
| 400 | malformed JSON, missing or mistyped fields | `BAD_REQUEST` |
| 401 | no or unknown `X-User-Id` | `UNAUTHENTICATED` |
| 403 | caller is not a member of the group (every group-scoped endpoint, reads included) | `NOT_A_MEMBER` |
| 404 | unknown group or invitation token | `GROUP_NOT_FOUND`, `INVITATION_NOT_FOUND` |
| 409 | already a member | `ALREADY_MEMBER` |
| 410 | expired invitation | `INVITATION_EXPIRED` |
| 422 | domain rule violated | `AMOUNT_NOT_POSITIVE`, `PAYER_NOT_IN_GROUP`, `PARTICIPANT_NOT_IN_GROUP`, `NO_PARTICIPANTS`, `GROUP_NOT_OPEN`, `SPLIT_NOT_SUPPORTED` |

**How both tracks use it**
- **Backend:** controller tests assert the responses match the contract (shape, status codes, error codes).
- **Frontend:** runs against a **Prism mock server** generated from the contract (`BACKEND_URL=http://localhost:4010`), generates its TypeScript types from the same file (`openapi-typescript`), and needs no running backend until integration.
- **Changing the contract:** edit `openapi.yaml` first, in its own commit, and tell the other tracks. Nobody changes an endpoint shape in code only.

## 8. Parallel build plan (structure for the plan document)

Work is organised so that after a short serial foundation, **four tracks run at the same time**, each in its own git worktree and branch, touching disjoint directories.

**Phase 0 – Foundation (serial, one session, first; target well under a day)**
Creates everything the tracks depend on, and nothing else:
1. `openapi.yaml` (§7) and its Prism mock script.
2. Spring Boot scaffold with Modulith, Flyway, Testcontainers, `docker-compose.yml` for Postgres; a `verify()` test and a green build.
3. `shared` package (`Money`, ids, error types) and the **public API packages** of all four modules (interfaces and event records, no implementation).
4. Next.js scaffold with the `/api` proxy helper, `BACKEND_URL`, the generated API types, and the dev user switcher shell.
5. Everything committed on a base branch that the tracks branch from.

**Phase 1 – Parallel tracks (independent worktrees)**

| Track | Scope | Touches only | Depends on | Can be tested without the others by |
|---|---|---|---|---|
| **A: account + group** | stub identity filter, seeded users, group create/list/detail, invitation create/accept, `GroupMembership` implementation, `GroupCreated`/`MemberJoined` events, controllers | `…/account/**`, `…/group/**`, their migrations | Phase 0 | `@ApplicationModuleTest` + Testcontainers |
| **B: expense** | Expense aggregate and invariants (R1–R4), equal split with rounding, application service, `ExpenseRecorded`, controller, persistence | `…/expense/**`, its migrations | Phase 0 (uses a **fake `GroupMembership`** in tests until A merges) | domain unit tests, module test with a stubbed port |
| **C: settlement** | balance read model, `ExpenseRecorded` listener, balances query and controller | `…/settlement/**`, its migrations | Phase 0 (**publishes a hand-built `ExpenseRecorded`** in tests) | Modulith `Scenario` tests |
| **D: frontend** | pages: groups list and create, group detail with members and invite link, add-expense form, balances tab, join page `/join/[token]`, user switcher, error display | `sharewise/frontend/**` | Phase 0 and Prism | type-check, lint, build, manual run against Prism |

Tracks A, B, C share no source files. The only shared files are `openapi.yaml` and the Phase 0 API packages, which change only through the rule in §7.

**Phase 2 – Integration (serial, one session)**
Merge A–D, fix mismatches, start backend + Postgres + frontend, run the **Mallorca slice** end to end (Alice creates the group, invites Bob, Carol, David, they join, Alice records the hotel and Bob the dinner, balances are shown and sum to 0). Add the e2e or smoke test and a short `RUNNING.md`. Update the exercise table in `CLAUDE.md`.

**Merge order:** C and B are independent; A last of the backends, because the real `GroupMembership` replaces the fake. D can merge any time.

## 9. Ex5 outlook (to be planned after Ex4, not part of this build)

Exact and percentage splits; correct and remove expenses (history, `CORRECTED`/`REMOVED`); leave, remove, promote with the balance-0 rule; invitation decline and expiry; close, reopen, archive including `AllBalancesSettled` → auto-archive; settlement payments with confirmation; the simplified settlement plan; Keycloak if time allows. These follow the same track structure, so the Phase 0 contract and API packages should already leave room for them (e.g. `split` is an enum with only `EQUAL` accepted).

## 10. Testing and definition of done for Ex4

- Backend: domain unit tests (including the rounding and the sum-is-zero property), module tests per module, `ApplicationModules.verify()`, controller tests against the contract. `./mvnw verify` is green from a clean checkout with Docker running.
- Frontend: `npm run lint`, type-check and `npm run build` pass.
- Integration: the Mallorca slice (§8 Phase 2) works by hand and as one automated test.
- A teammate can follow `RUNNING.md` and see the slice running.

**Environment (checked 24.10 on the user's machine):** Java 21, Node 24, Docker 27 installed; Maven is not on the PATH, so the Maven wrapper from the scaffold is the only Maven. Whether Docker Desktop is running was not checked. Prism runs through `npx`, no install needed.

**Submission 🟢:** Ex4 is handed in as a git repository. The deliverable is therefore the committed and pushed code plus `RUNNING.md`; Phase 2 ends with a clean checkout that runs.

**Repository 🟢:** ShareWise lives in **its own git repository**, like the prof's `favsdemo`. The folder `sharewise/` (next to the study notes, currently untracked in the FAVS repo) becomes that repo's root via `git init`, with `backend/`, `frontend/`, `docs/` and `docker-compose.yml` at its root. All `sharewise/...` paths in this spec are relative to the FAVS folder, so inside the new repo they read `docs/api/openapi.yaml`, `backend/`, `frontend/` and so on. Phase 0 starts with the `git init`, a `.gitignore`, and a copy of this spec and the two docs in `sharewise/docs/` into the new repo. The FAVS study repo then ignores `sharewise/` and `favsdemo/`. Where the remote lives (the team's GitLab or GitHub) is up to the team; the parallel tracks work on local branches and worktrees until then.

## 11. Left to the builder

- Internal class design inside each module beyond the module's public API.
- The frontend component structure, styling (Tailwind is optional) and form libraries.
- Test fixtures and naming.
- The exact Flyway versioning scheme.
- Whether the Mallorca e2e test is a Spring test with `MockMvc`/Testcontainers or a Playwright run.

## 12. Open items for the team (not blocking the build)

- Root package name and Maven group id (D12).
- **Accepted risk:** Phase 0 starts before the class on 26.10, so we build package-per-context (decided in P1). If the prof insists on technical layering, we re-package; the module logic does not change. Confirm in class whether the prof expects technical layering rather than package-per-context (see `02_Reference_Template_Takeaways.md` §6), and whether a git repo link is all that is needed.
- Whether Keycloak makes it into Ex5.
