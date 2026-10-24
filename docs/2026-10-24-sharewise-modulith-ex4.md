# ShareWise Modulith (Ex4 slice) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A running Spring Boot + Next.js modular monolith with the slice create group → invite and join → record equal-split expense → see balances, built so four tracks can run in parallel.

**Architecture:** Four Spring Modulith modules (`account`, `group`, `expense`, `settlement`) plus `shared`, talking only through `@NamedInterface("api")` packages and in-process events. A contract-first `openapi.yaml` is the only coupling between backend and frontend. Browser → Next.js route handlers under `app/api/...` (one per endpoint, as in the prof's `greetings/route.ts`) → Spring Boot at `BACKEND_URL`.

**Tech Stack:** Java 21, Spring Boot (newest stable from start.spring.io), Spring Modulith, Spring Data JPA, Flyway, Postgres (compose), Testcontainers, Maven wrapper; Next.js App Router + TypeScript, `openapi-typescript`, Prism mock.

**Spec:** `docs/superpowers/specs/2026-10-24-sharewise-modulith-ex4-design.md` (FAVS repo). Domain rules: `work/ShareWise_Requirements.md`.

**Paths:** All paths below are relative to the **new ShareWise repo root** (= the folder `sharewise/` today). `<pkg>` = `backend/src/main/java/de/hsh/favs/sharewise`, `<test>` = `backend/src/test/java/de/hsh/favs/sharewise`. Root Java package `de.hsh.favs.sharewise`.

## Global Constraints

- Java 21; Maven only via the generated wrapper (`./mvnw`); Maven is not on the PATH.
- Money is `long` cents everywhere (API `amountCents`, domain `Money(long cents, Currency currency)`); currency fixed to EUR. No floating point.
- IDs are UUIDs; `MemberId` is a distinct type carrying the same UUID as `UserId`.
- Modules own their tables, no foreign keys across modules, Flyway versions prefixed per module: shared `V0xx`, account `V1xx`, group `V2xx`, expense `V3xx`, settlement `V4xx`; dev seed in `db/migration-dev` (dev profile only).
- `ApplicationModules.verify()` must pass; declared dependencies only: `group → account`, `settlement → account`, `settlement → group`, `settlement → expense` (event consumption), `expense → group`, `expense → account` (caller identity).
- Events are records published via `ApplicationEventPublisher`, consumed with `@ApplicationModuleListener`; they carry ids only.
- Stub identity: header `X-User-Id`, filter registered only under profile `dev`; missing or unknown → 401 `UNAUTHENTICATED`.
- Errors are `application/problem+json` with the status/code table of spec §7: 400 `BAD_REQUEST`, 401 `UNAUTHENTICATED`, 403 `NOT_A_MEMBER`, 404 `GROUP_NOT_FOUND`/`INVITATION_NOT_FOUND`, 409 `ALREADY_MEMBER`, 410 `INVITATION_EXPIRED`, 422 `AMOUNT_NOT_POSITIVE`/`PAYER_NOT_IN_GROUP`/`PARTICIPANT_NOT_IN_GROUP`/`NO_PARTICIPANTS`/`GROUP_NOT_OPEN`/`SPLIT_NOT_SUPPORTED`.
- Invitations expire after 7 days, enforced at accept time.
- Rounding (R4): `total ÷ n`, remainder cents one each to the first participants in the given order; shares sum to the total. Balances (R14): payer +total, each participant −share; sum per group is 0.
- `openapi.yaml` changes go in their own commit before any code that depends on them.
- Dev users (fixed ids): Alice `aaaaaaaa-0000-0000-0000-000000000001`, Bob `…0002`, Carol `…0003`, David `…0004`.

## Review Focus

1. Duplicate ids in `participantIds` → 422 `NO_PARTICIPANTS`-style rejection or dedupe, never a 500 or double charge (Task B1).
2. Malformed UUID in a path or body → 400 `BAD_REQUEST`, not 500 (Task 0.4 handler test).
3. Accepting an invitation twice or after expiry → 409 / 410 (Task A3).
4. Balances of a group with members but no expenses → every member listed with 0 (Task C2).
5. Backend unreachable or error response → the UI shows a readable error, not a blank page (Task D4).

## Parallel structure

```
Phase 0 (serial):  0.1 → 0.2 → { 0.3 → 0.4 (backend) ‖ 0.5 (frontend) } → tag phase0-done
Phase 1 (parallel, one worktree each, branch from phase0-done):
   Track A  account+group      Track B  expense      Track C  settlement      Track D  frontend
Phase 2 (serial):  I1 merge → I2 Mallorca test → I3 docs
```

Create each worktree with `git worktree add ../sharewise-<track> -b track/<track> phase0-done`. A track executor needs only the Global Constraints, the Phase 0 interfaces (Task 0.4) and its own tasks. Merge order: C, B, D (any order), then A last (it replaces the fake `GroupMembership`).

---

## Phase 0: Foundation

### Task 0.1: Repository
**Files:** Create `.gitignore`, `docs/specs/…` (copies); Modify FAVS repo `.gitignore`.
- [ ] **Step 1:** In `sharewise/`, run `git init -b main`. Copy the spec, this plan, `01_Requirements_from_Prof.md` and `02_Reference_Template_Takeaways.md` into `sharewise/docs/`. Add a `.gitignore` for `target/`, `node_modules/`, `.next/`, `.idea/`, `.env*.local`.
- [ ] **Step 2:** In the FAVS repo, add `sharewise/` and `favsdemo/` to its `.gitignore`.
- [ ] **Step 3:** Commit in the new repo: `chore: init repository with design docs`.

### Task 0.2: API contract
**Files:** Create `docs/api/openapi.yaml`, `docs/api/mock.sh` (runs `npx @stoplight/prism-cli mock docs/api/openapi.yaml -p 4010 -d`).
**Produces:** the contract every other task codes against.
- [ ] **Step 1:** Write `openapi.yaml` (3.1) with every endpoint, schema and error of spec §7: `User{userId,displayName}`, `Group{id,name,status,memberCount}`, `GroupDetail` (+`members:[{memberId,displayName,role}]`), `Invitation{token,expiresAt}`, `ExpenseRequest{title,amountCents,date,payerId,participantIds,split enum[EQUAL]}`, `Expense{id,title,amountCents,date,payerId,shares:[{memberId,shareCents}]}`, `Balance{memberId,displayName,balanceCents}`, `Problem{type,title,status,code,detail}`. Every operation lists its error responses from the table in Global Constraints. Include the Mallorca-style examples (Alice +…, sums to 0) as response examples so Prism returns realistic data.
- [ ] **Step 2:** Run `npx @redocly/cli lint docs/api/openapi.yaml`. Expected: no errors.
- [ ] **Step 3:** Run `docs/api/mock.sh`, then `curl localhost:4010/groups -H "X-User-Id: aaaaaaaa-0000-0000-0000-000000000001"`. Expected: 200 with example JSON. Stop the mock.
- [ ] **Step 4:** Commit: `feat(api): add Ex4 OpenAPI contract and mock script`.

### Task 0.3: Backend scaffold
**Files:** Create `backend/**` (Initializr), `docker-compose.yml`, `backend/src/main/resources/application.yml`, `application-dev.yml`, `<pkg>/SharewiseApplication.java`, `<test>/ModularityTests.java`, `<test>/support/PostgresTestConfig.java`.
- [ ] **Step 1:** Generate the project from start.spring.io (Maven, Java 21, newest stable Boot) with: web, data-jpa, validation, postgresql, flyway, lombok, testcontainers, actuator, plus `spring-modulith-starter-core`, `-starter-jpa` and `-starter-test`. Confirm current artifact names and versions with Context7. Unzip into `backend/`.
- [ ] **Step 2:** `docker-compose.yml` with service `db` (postgres, db/user/password `sharewise`, port 5432, named volume). `application-dev.yml` points at it and adds Flyway location `classpath:db/migration-dev`. `application.yml` sets Flyway locations to the five `classpath:db/migration/<module>` folders and `spring.jpa.hibernate.ddl-auto=validate`.
- [ ] **Step 3:** Write failing test `ModularityTests.verifiesModuleStructure` (`ApplicationModules.of(SharewiseApplication.class).verify()`). Run `cd backend && ./mvnw -q test -Dtest=ModularityTests`. Expected: PASS on the empty scaffold (this is the baseline; it fails later if boundaries break).
- [ ] **Step 4:** `PostgresTestConfig`: `@TestConfiguration` with a `PostgreSQLContainer` bean annotated `@ServiceConnection`; all module tests import it.
- [ ] **Step 5:** Run `./mvnw -q verify` (Docker running). Expected: BUILD SUCCESS. Commit: `chore(backend): scaffold Spring Boot with Modulith, Flyway, Testcontainers`.

### Task 0.4: Shared types and module APIs
**Files:** Create under `<pkg>/shared/`: `GroupId.java`, `MemberId.java`, `UserId.java`, `Money.java`, `Currency` use `java.util.Currency`, `ErrorCode.java`, `DomainException.java`, `web/ProblemExceptionHandler.java`, `package-info.java` (`@ApplicationModule(type = OPEN)`); under each module `<pkg>/<module>/api/package-info.java` with `@NamedInterface("api")` and `<pkg>/<module>/package-info.java` with `@ApplicationModule(allowedDependencies = {…})`; the API types below; tests `<test>/shared/MoneyTest.java`, `<test>/shared/ProblemExceptionHandlerTest.java`; migration `db/migration/shared/V001__event_publication.sql`.
**Produces (exact, all other tasks use these names):**
```java
// shared
record GroupId(UUID value) {} record MemberId(UUID value) {} record UserId(UUID value) {}
record Money(long cents, Currency currency) {
  static Money eur(long cents); Money plus(Money o); Money minus(Money o); boolean isPositive(); }
enum ErrorCode { BAD_REQUEST(400), UNAUTHENTICATED(401), NOT_A_MEMBER(403), GROUP_NOT_FOUND(404),
  INVITATION_NOT_FOUND(404), ALREADY_MEMBER(409), INVITATION_EXPIRED(410), AMOUNT_NOT_POSITIVE(422),
  PAYER_NOT_IN_GROUP(422), PARTICIPANT_NOT_IN_GROUP(422), NO_PARTICIPANTS(422), GROUP_NOT_OPEN(422),
  SPLIT_NOT_SUPPORTED(422); int status(); }
class DomainException extends RuntimeException { DomainException(ErrorCode code, String detail); ErrorCode code(); }
// account.api
record UserInfo(UserId id, String displayName) {}
interface CurrentUser { UserInfo get(); }                       // throws DomainException(UNAUTHENTICATED)
interface UserDirectory { Optional<UserInfo> find(UserId id); List<UserInfo> all(); }
// group.api
enum GroupStatus { OPEN, CLOSED_FOR_SETTLEMENT, ARCHIVED }  enum Role { ADMIN, MEMBER }
record GroupCreated(GroupId groupId, UserId creator) {}  record MemberJoined(GroupId groupId, MemberId memberId, Role role) {}
interface GroupMembership {
  boolean exists(GroupId g); boolean isMember(GroupId g, MemberId m);
  List<MemberId> members(GroupId g); GroupStatus status(GroupId g);
  default void requireMember(GroupId g, MemberId m) { /* !exists → GROUP_NOT_FOUND; !isMember → NOT_A_MEMBER */ } }
// expense.api
record ExpenseRecorded(UUID expenseId, GroupId groupId, MemberId payerId, long totalCents, List<Share> shares) {
  record Share(MemberId memberId, long shareCents) {} }
// settlement.api
record MemberBalance(MemberId memberId, String displayName, long balanceCents) {}
interface Balances { List<MemberBalance> of(GroupId g); }
```
- [ ] **Step 1:** Failing tests: `MoneyTest.plusAndMinusKeepCurrency`, `MoneyTest.isPositiveFalseForZero`, `ProblemExceptionHandlerTest.mapsDomainExceptionToProblemJson` (status and `code` property from `ErrorCode`), `ProblemExceptionHandlerTest.malformedUuidReturns400BadRequest` (Review Focus 2), `GroupMembershipTest.requireMemberRejectsUnknownGroupAndNonMember` using an in-memory fake.
- [ ] **Step 2:** Run `./mvnw -q test`. Expected: FAIL (types missing).
- [ ] **Step 3:** Implement the types above. `ProblemExceptionHandler` is a `@RestControllerAdvice` handling `DomainException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`, `MethodArgumentNotValidException`, `ConstraintViolationException` → problem+json. Declare `allowedDependencies` per Global Constraints. `V001` creates the Modulith `event_publication` table (use the schema from the Modulith JDBC/JPA docs for the chosen version).
- [ ] **Step 4:** Run `./mvnw -q verify`. Expected: PASS including `ModularityTests`.
- [ ] **Step 5:** Commit: `feat(backend): shared types, error model and module API packages`.

### Task 0.5: Frontend scaffold (parallel with 0.3/0.4)
**Files:** Create `frontend/**` (create-next-app: TypeScript, ESLint, `src/`, App Router, Tailwind yes), `frontend/src/lib/backend.ts`, one `route.ts` per endpoint under `frontend/src/app/api/` (`me`, `users`, `groups`, `groups/[groupId]`, `groups/[groupId]/invitations`, `groups/[groupId]/expenses`, `groups/[groupId]/balances`, `invitations/[token]/accept`), `frontend/src/api/schema.d.ts` (generated), `frontend/src/lib/api.ts`, `frontend/src/components/UserSwitcher.tsx`, `frontend/.env.example` (`BACKEND_URL=http://localhost:8080`), `frontend/src/lib/backend.test.ts` (vitest).
**Produces:** `proxyToBackend(req: Request, backendPath: string): Promise<Response>` (each route file is a few lines: `await connection()`, then call it, like the demo); `api<T>(path, init?): Promise<T>` throwing `ApiError{status, code, detail}`; cookie `sw_user_id` read by the proxy and sent as `X-User-Id`.
- [ ] **Step 1:** Scaffold; add `openapi-typescript` and script `"gen:api": "openapi-typescript ../docs/api/openapi.yaml -o src/api/schema.d.ts"`; run it.
- [ ] **Step 2:** Failing tests `proxyToBackend.forwardsMethodBodyAndStatus`, `groupsRoute.forwardsPostToBackendGroups`, `proxyToBackend.addsUserIdHeaderFromCookie`, `proxyToBackend.returns502ProblemWhenBackendUnreachable` (mock `fetch`). Run `npx vitest run`. Expected: FAIL.
- [ ] **Step 3:** Implement `backend.ts` (reads `process.env.BACKEND_URL`, `cache: 'no-store'`, passes status and body through, 502 `problem+json` on exception) and the eight route files, each exporting only the methods of spec §7 for its endpoint and passing path params (awaited, as in current Next.js) into the backend path. All route files are created here so Track D never edits them. `UserSwitcher` lists `GET /api/users` and writes the cookie.
- [ ] **Step 4:** Run `npx vitest run && npm run lint && npx tsc --noEmit && npm run build`. Expected: all pass.
- [ ] **Step 5:** Commit: `chore(frontend): scaffold Next.js with backend proxy and generated API types`. Then merge 0.3/0.4/0.5 into `main` and `git tag phase0-done`.

---

## Phase 1, Track A: account + group (`<pkg>/account/**`, `<pkg>/group/**`)

### Task A1: Account (stub identity)
**Files:** `<pkg>/account/{User.java, UserRepository.java, UserDirectoryImpl.java, DevIdentityFilter.java, CurrentUserImpl.java, AccountController.java}`, `db/migration/account/V101__users.sql`, `db/migration-dev/V190__dev_users.sql`, `<test>/account/AccountModuleTest.java`.
**Consumes:** `UserDirectory`, `CurrentUser`, `UserInfo`. **Produces:** implementations of both; `GET /me`, `GET /users`.
- [ ] **Step 1:** Failing `@ApplicationModuleTest` cases: `meReturnsCurrentUserForKnownHeader` (Alice id → 200 `displayName` "Alice"), `missingHeaderIs401Unauthenticated`, `unknownUserIdIs401`, `usersListsFourSeededUsers`.
- [ ] **Step 2:** Run `./mvnw -q test -Dtest=AccountModuleTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. `DevIdentityFilter` is `@Profile("dev")`, stores the `UserInfo` in a request-scoped holder that `CurrentUserImpl` reads; `/users` only exists under `dev`. Seed the four users with the fixed ids.
- [ ] **Step 4:** Rerun the test. Expected: PASS. Commit: `feat(account): stub identity filter and seeded dev users`.

### Task A2: Group create, list, detail
**Files:** `<pkg>/group/{Group.java, Member.java, GroupRepository.java, GroupService.java, GroupMembershipImpl.java, GroupController.java}`, `db/migration/group/V201__groups_members.sql`, `<test>/group/GroupModuleTest.java`.
**Consumes:** `CurrentUser`, `UserDirectory`. **Produces:** `GroupMembership` implementation (replaces Track B's fake at merge), `GroupCreated` and `MemberJoined(ADMIN)` events, `POST /groups`, `GET /groups`, `GET /groups/{id}`.
- [ ] **Step 1:** Failing tests: `createGroupMakesCallerAdminMemberAndPublishesEvents` (Scenario: `GroupCreated`, `MemberJoined`), `blankNameIs400BadRequest` (Review Focus), `listReturnsOnlyMyGroups`, `detailForNonMemberIs403NotAMember`, `detailForUnknownGroupIs404`, `membershipExistsIsMemberMembersStatus` (status OPEN).
- [ ] **Step 2:** Run `./mvnw -q test -Dtest=GroupModuleTest`. Expected: FAIL.
- [ ] **Step 3:** Implement layered (controller → service → repository). Detail resolves display names via `UserDirectory`. Currency stored as `EUR`.
- [ ] **Step 4:** Rerun. Expected: PASS. Commit: `feat(group): create, list and read groups with membership API`.

### Task A3: Invitations
**Files:** `<pkg>/group/{Invitation.java, InvitationRepository.java, InvitationService.java, InvitationController.java}`, `db/migration/group/V202__invitations.sql`, `<test>/group/InvitationModuleTest.java`.
**Produces:** `POST /groups/{id}/invitations`, `POST /invitations/{token}/accept`, `MemberJoined(MEMBER)`.
- [ ] **Step 1:** Failing tests: `memberCreatesInviteWithSevenDayExpiry`, `nonMemberCannotInvite403`, `acceptMakesCallerMemberAndPublishesMemberJoined`, `acceptTwiceIs409AlreadyMember`, `creatorAcceptingOwnInviteIs409`, `expiredTokenIs410` (clock injected via `java.time.Clock` bean), `unknownTokenIs404`.
- [ ] **Step 2:** Run the class. Expected: FAIL. **Step 3:** Implement; token is a random URL-safe string (≥128 bits). **Step 4:** PASS. Commit: `feat(group): invitations with 7-day expiry`.

---

## Phase 1, Track B: expense (`<pkg>/expense/**`)

### Task B1: Expense domain and equal split
**Files:** `<pkg>/expense/domain/{Expense.java, EqualSplit.java, Share.java}`, `<test>/expense/domain/{ExpenseTest.java, EqualSplitTest.java}`.
**Produces:** `static Expense Expense.record(GroupId, String title, Money amount, LocalDate date, MemberId payer, List<MemberId> participants)` throwing `DomainException`; `List<Share> EqualSplit.split(Money total, List<MemberId> participants)`.
- [ ] **Step 1:** Failing tests (pure unit): `splitsOneHundredEuroAmongFourEvenly`, `remainderCentsGoToFirstParticipants` (1000 cents / 3 → 334, 333, 333), `oneCentAmongThreeGivesFirstTheCent`, `sharesAlwaysSumToTotal` (property over random totals and sizes), `rejectsZeroAndNegativeAmountAMOUNT_NOT_POSITIVE`, `rejectsEmptyParticipantsNO_PARTICIPANTS`, `duplicateParticipantsAreRejected` (Review Focus 1).
- [ ] **Step 2:** Run `./mvnw -q test -Dtest='ExpenseTest,EqualSplitTest'`. Expected: FAIL. **Step 3:** Implement. **Step 4:** PASS. Commit: `feat(expense): expense aggregate with equal split`.

### Task B2: Application service and persistence
**Files:** `<pkg>/expense/application/{RecordExpenseUseCase.java (port in), RecordExpenseService.java, ExpenseRepositoryPort.java (port out)}`, `<pkg>/expense/adapter/persistence/{ExpenseEntity.java, ShareEntity.java, JpaExpenseRepository.java, ExpenseRepositoryAdapter.java}`, `db/migration/expense/V301__expenses.sql`, `<test>/expense/ExpenseServiceTest.java`, `<test>/expense/FakeGroupMembership.java`.
**Consumes:** `GroupMembership` (real one arrives with Track A; this track uses `FakeGroupMembership` implementing it, honouring `requireMember` semantics). **Produces:** `ExpenseRecorded` event.
- [ ] **Step 1:** Failing tests: `recordsExpenseAndPublishesExpenseRecordedWithShares` (Scenario), `callerNotMemberIs403`, `payerNotInGroupIs422`, `participantNotInGroupIs422`, `groupNotOpenIs422GROUP_NOT_OPEN` (fake returns CLOSED).
- [ ] **Step 2:** Run. Expected: FAIL. **Step 3:** Implement hexagonally (service depends on ports only; adapter maps to JPA). Event published in the same transaction. **Step 4:** PASS. Commit: `feat(expense): record expense use case with persistence and event`.

### Task B3: REST adapter
**Files:** `<pkg>/expense/adapter/web/{ExpenseController.java, ExpenseRequest.java, ExpenseResponse.java}`, `<pkg>/expense/application/ListExpensesQuery.java`, `<test>/expense/ExpenseControllerTest.java`.
**Produces:** `POST /groups/{id}/expenses`, `GET /groups/{id}/expenses` (newest first).
- [ ] **Step 1:** Failing `MockMvc` tests: `postReturns201AndMatchesContractShape`, `splitPercentageIs422SplitNotSupported`, `listIsNewestFirstWithShares`, `invalidUuidIs400`, `callerNotMemberIs403`.
- [ ] **Step 2:** Run. FAIL. **Step 3:** Implement DTO records; request validates `split == EQUAL`. **Step 4:** PASS. Commit: `feat(expense): REST endpoints for recording and listing expenses`.

---

## Phase 1, Track C: settlement (`<pkg>/settlement/**`)

### Task C1: Balance read model
**Files:** `<pkg>/settlement/{BalanceEntry.java, BalanceRepository.java, ExpenseRecordedListener.java}`, `db/migration/settlement/V401__balances.sql`, `<test>/settlement/BalanceListenerTest.java`.
**Consumes:** `ExpenseRecorded`. Stores per (group, member) running balance cents.
- [ ] **Step 1:** Failing Scenario tests with a hand-built event: `payerIsCreditedTotalAndParticipantsDebitedShares`, `payerWhoIsAlsoParticipantNetsCorrectly`, `balancesSumToZeroAfterManyExpenses`, `reprocessingSameExpenseIdIsIdempotent`.
- [ ] **Step 2:** FAIL. **Step 3:** Implement with `@ApplicationModuleListener`; idempotency keyed by `expenseId`. **Step 4:** PASS. Commit: `feat(settlement): update balances from ExpenseRecorded`.

### Task C2: Balances query
**Files:** `<pkg>/settlement/{BalancesImpl.java, BalancesController.java}`, `<test>/settlement/BalancesApiTest.java`.
**Consumes:** `GroupMembership`, `UserDirectory`, `CurrentUser`. **Produces:** `Balances` implementation, `GET /groups/{id}/balances`.
- [ ] **Step 1:** Failing tests: `listsEveryMemberWithZeroWhenNoExpenses` (Review Focus 4), `mergesStoredBalancesWithMemberNames`, `sumOfReturnedBalancesIsZero`, `nonMemberIs403`, `unknownGroupIs404`. Use a fake `GroupMembership` and `UserDirectory`.
- [ ] **Step 2:** FAIL. **Step 3:** Implement per D15. **Step 4:** PASS. Commit: `feat(settlement): balances endpoint listing all members`.

---

## Phase 1, Track D: frontend (`frontend/src/**`)

Run against the mock: `docs/api/mock.sh` and `BACKEND_URL=http://localhost:4010 npm run dev`. After each task run `npx vitest run && npm run lint && npx tsc --noEmit`.

### Task D1: Groups list and create
**Files:** `src/app/page.tsx`, `src/app/groups/new/page.tsx`, `src/components/GroupList.tsx`, `src/components/GroupList.test.tsx`.
- [ ] **Step 1:** Failing component tests: `rendersGroupsFromApi`, `showsEmptyStateWhenNoGroups`, `createFormPostsNameAndNavigatesToGroup`, `blankNameShowsValidationMessage`. **Step 2:** FAIL. **Step 3:** Implement with the `api()` helper and generated types; `UserSwitcher` in the layout header. **Step 4:** PASS. Commit: `feat(frontend): groups list and create`.

### Task D2: Group detail, invite, join
**Files:** `src/app/groups/[id]/page.tsx`, `src/app/join/[token]/page.tsx`, `src/components/{MemberList,InviteButton}.tsx` plus tests.
- [ ] **Step 1:** Failing tests: `showsMembersWithRoles`, `inviteButtonShowsCopyableLink` (`/join/<token>`), `joinPageAcceptsTokenAndRedirects`, `joinPageShowsExpiredMessageOn410`, `joinPageShowsAlreadyMemberOn409`. **Step 2–4:** FAIL, implement, PASS. Commit: `feat(frontend): group detail, invitations and join page`.

### Task D3: Expenses
**Files:** `src/components/{ExpenseForm,ExpenseList}.tsx` plus tests, tab wiring in `groups/[id]/page.tsx`.
- [ ] **Step 1:** Failing tests: `formDefaultsPayerToCurrentUserAndParticipantsToAll`, `convertsEuroInputToCentsWithoutFloatErrors` (19.99 → 1999, "0,10" → 10), `submitPostsExpenseAndRefreshesList`, `showsSharePreviewPerParticipant`, `serverErrorCodeShownAsMessage` (422 `PAYER_NOT_IN_GROUP`). **Step 2–4:** FAIL, implement, PASS. Commit: `feat(frontend): record and list expenses`.

### Task D4: Balances and error handling
**Files:** `src/components/{BalancesTab,ErrorBanner}.tsx` plus tests.
- [ ] **Step 1:** Failing tests: `balancesShowSignedAmountsAndSumZero`, `zeroBalanceMembersAreListed`, `backendUnreachableShowsErrorBanner` (502 problem, Review Focus 5), `unauthenticatedPromptsToPickUser` (401). **Step 2–4:** FAIL, implement, PASS. Commit: `feat(frontend): balances tab and error banner`.

---

## Phase 2: Integration

### Task I1: Merge and contract check
- [ ] **Step 1:** Merge `track/c`, `track/b`, `track/d`, then `track/a` into `main`. Delete `FakeGroupMembership` use in the Expense and Settlement module tests where the real bean is now available (keep the fake only for pure unit tests).
- [ ] **Step 2:** Run `cd backend && ./mvnw -q verify` and `cd frontend && npm run lint && npx tsc --noEmit && npm run build`. Expected: all green, `ModularityTests` still passes.
- [ ] **Step 3:** Run `npx @redocly/cli lint docs/api/openapi.yaml` and fix any drift between code and contract (contract change first, in its own commit).

### Task I2: Mallorca slice test
**Files:** `<test>/integration/MallorcaSliceTest.java` (`@SpringBootTest` + MockMvc + Testcontainers, profile `dev`).
- [ ] **Step 1:** Write the test: Alice creates "Mallorca 2027"; invites; Bob, Carol, David accept; Alice records Hotel 90000 cents (all four, 22500 each); Bob records Dinner 18000 (all four); await balances (Awaitility, eventual consistency); assert balances Alice **+63000**, Bob **−9000**, Carol **−27000**, David **−27000** cents (sum 0). Verify the arithmetic with a quick script before writing the numbers into the test.
- [ ] **Step 2:** Run it. Expected: PASS. Commit: `test: Mallorca end-to-end slice`.

### Task I3: Docs and handoff
**Files:** `RUNNING.md`, FAVS `CLAUDE.md` (status table row Ex4).
- [ ] **Step 1:** `RUNNING.md`: prerequisites (Java 21, Node, Docker Desktop running), `docker compose up -d db`, `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, `npm run dev`, the dev user switcher, how to run tests, the Prism mock command.
- [ ] **Step 2:** From a clean clone in a temp folder, follow `RUNNING.md` verbatim and confirm the slice works in the browser. Update the Ex4 row in FAVS `CLAUDE.md`. Push the repo to the team remote and tag `ex4`.
