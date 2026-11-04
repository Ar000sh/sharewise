# Keycloak authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ShareWise's own email/password login with Keycloak's login page; the backend only validates Keycloak JWTs and creates the `Account` on first login.

**Architecture:** Next.js is the OIDC client (authorization code + PKCE) and keeps access and refresh token in an httpOnly cookie; it forwards the access token as `Authorization: Bearer` to the Spring Boot backend, which is an OAuth2 resource server. `sub` is the `UserId`. The account module turns a validated token into an `Account` (get or create, one transaction) and the rest of the system keeps using the `CurrentUser` and `UserProfiles` ports unchanged.

**Tech Stack:** Keycloak (pin the latest stable 26.x image), Spring Security resource server (Spring Boot 4.1.2; check the starter's artifact name in the BOM), Testcontainers Keycloak, Next.js 16.4.3 route handlers (read `frontend/node_modules/next/dist/docs/` before writing any, `frontend/AGENTS.md` says the APIs differ from what you know), Playwright.

**Spec:** `docs/2026-10-30-keycloak-auth-design.md` (decisions D1 to D10, builder calls C1 to C8, failure modes F1 to F20). Read it first; this plan does not repeat it.

## Global Constraints
- ShareWise never sees, stores or logs a password; no `passwordHash`, `Hasher`, `RawPassword` anywhere after Task 4.
- Tokens only in an httpOnly cookie (`SameSite=Lax`); never readable by browser JavaScript. Only access + refresh token are stored (no id token), so the cookie stays under 4 KB. Logout uses `client_id` + `post_logout_redirect_uri`.
- Backend answers a missing/invalid token, an inactive account, a missing `email` claim or a non-UUID `sub` with 401 problem+json, `code: UNAUTHENTICATED`, never 500. Open without token: `/actuator/**` only.
- *(Changed 03.11.26, design `2026-11-03-notifications-design.md` N4: the realm no longer uses the email as username; the username is also taken from `preferred_username` on first login and must be a valid `Username`, else 401.)*
- `sub` = `UserId`. Email and display name are taken from the token on first login only (D10, C2). Display name: claim `name`, else `preferred_username`, else the email before `@`; trimmed and cut to the `DisplayName` limit (C7).
- Realm `sharewise`: self-registration on, `verifyEmail` false, `loginWithEmailAllowed` true, `duplicateEmailsAllowed` false, `bruteForceProtected` true, public client `sharewise-web` with PKCE S256, no direct grants. Keycloak on fixed `http://localhost:8180`, issuer `http://localhost:8180/realms/sharewise`.
- Contract first, in its own commit (`.githooks` rejects a mixed commit). Regenerate with `npm run gen:api`; never edit `schema.d.ts`.
- Conventions apply: test names are sentences, `// given // when // then`, Javadoc on public types, vocabulary from the diagram, `./mvnw spring-javaformat:apply` before committing, `ModularityTests` and `ConventionTests` stay green. Docs change in the same commit as the code (grep for the old terms).
- Tests: failure-mode tests only for F1 and F2 (user scope decision). Ordinary behaviour tests for new code are normal.
- Commit format `<type>(<scope>): <subject>`, trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` (the model doing the work).
- Never write regexes or backslashes through shell heredocs; use Write/Edit.

## Review Focus
1. **Two first requests at once** → one account, one `AccountRegistered` (F1). Pinned in Task 5.
2. **Cross-site POST to a Next.js route handler** is rejected before the backend is called (F2). Pinned in Task 7.
3. Same email with a new `sub` is a 401, not a 500 (F3). Built in Task 5, **no test** (scope decision).
4. Cookie larger than 4 KB loses the session (F4). **No test**; checked once by hand in Task 8.
5. Parallel requests with an expired token refresh once (F7). Built in Task 7, **no test**.

## File Structure
| Path | Responsibility |
|---|---|
| `docs/api/openapi.yaml` | `/auth/*` and its schemas and error codes removed |
| `docker-compose.yml`, `docker/keycloak/sharewise-realm.json` | Keycloak service and the realm that compose and tests both import |
| `backend/.../account/domain/Account.java`, `DisplayName.java` | `registerFromIdentityProvider`, `DisplayName.derive` |
| `backend/.../account/application/AccountService.java` | `resolveOrCreate(IdentityClaims)` |
| `backend/.../account/application/IdentityClaims.java` | the four claims the service needs, as a record |
| `backend/.../account/application/SecurityConfig.java`, `JwtCallerConverter.java` | resource server setup, token → `UserProfile` |
| `backend/.../account/application/CurrentUserImpl.java` | reads the caller from the security context |
| `backend/src/main/resources/db/migration/account/V105__external_identity.sql` | drops sessions and password columns, empties accounts |
| `backend/src/test/.../TestcontainersConfiguration.java`, `KeycloakUsers.java` | real Keycloak in tests, real tokens |
| `frontend/src/lib/oidc.ts`, `session.ts`, `backend.ts`, `app/api/auth/{login,callback,logout}/route.ts` | OIDC client, cookie, forwarding with Origin check and refresh |

---

### Task 1: Contract (own commit)
**Files:** Modify `docs/api/openapi.yaml`.
**Produces:** a contract without `/auth/register`, `/auth/login`, schemas `RegisterRequest`, `LoginRequest`, `AuthResult`, error codes `EMAIL_TAKEN`, `INVALID_CREDENTIALS`; `/me`, `UNAUTHENTICATED` and `bearerAuth` stay. The description says the bearer token is a Keycloak access token and that the `auth` tag is gone.

- [ ] Step 1: Remove the items above (also from the `ErrorCode` enum listing in the file) and the `auth` tag; update `info.description`.
- [ ] Step 2: `npx @redocly/cli lint docs/api/openapi.yaml` → no errors.
- [ ] Step 3: Commit `contract(account): drop /auth endpoints, tokens come from Keycloak` (contract only; type `contract` per `03_CODE_STYLE.md` section 6).

### Task 2: Keycloak in compose with the realm file
**Files:** Modify `docker-compose.yml`; Create `docker/keycloak/sharewise-realm.json`.
**Produces:** `docker compose up -d keycloak` serves the realm at the issuer URL of the Global Constraints. Service: pinned image, `start-dev --import-realm`, port `8180:8080`, `KC_HOSTNAME=http://localhost:8180`, admin user/password from compose env (local only), the realm folder mounted to `/opt/keycloak/data/import`, health enabled.

- [ ] Step 1: Write the realm file with the settings of Global Constraints; client `sharewise-web`: standard flow on, PKCE `S256`, redirect URIs `http://localhost:3000/*`, post-logout redirect `http://localhost:3000/*`, web origins `http://localhost:3000`; registration form asks first name, last name, email, password.
- [ ] Step 2: Add the service. Run `docker compose up -d keycloak`, then `curl -s http://localhost:8180/realms/sharewise/.well-known/openid-configuration` → JSON with `"issuer":"http://localhost:8180/realms/sharewise"`.
- [ ] Step 3: Commit `chore(build): Keycloak and the sharewise realm in compose`.

### Task 3: Keycloak in backend tests (real tokens)
**Files:** Modify `backend/pom.xml`, `TestcontainersConfiguration.java`; Create `backend/src/test/java/de/hsh/favs/sharewise/KeycloakUsers.java`, `KeycloakUsersTest.java`.
**Interfaces — Produces:** `TestcontainersConfiguration` starts one shared Keycloak container that imports `docker/keycloak/sharewise-realm.json` (same file as compose), waits on its health endpoint, and registers `spring.security.oauth2.resourceserver.jwt.issuer-uri` with the container's real issuer (dynamic port). `KeycloakUsers.create(String displayName): KeycloakUsers.Login` where `record Login(String subject, String email, String accessToken)`. It creates a user with the admin API (container admin credentials) and, **at test time only**, creates a client with direct access grants and fetches the token with it (C1); the realm file is not changed.

- [ ] Step 1: Failing test `KeycloakUsersTest.createdUserGetsATokenWithSubEmailAndIssuer`: token's `iss` equals the container issuer, `sub` equals `Login.subject`, `email` claim equals `Login.email`.
- [ ] Step 2: Add the Keycloak test dependency (check `com.github.dasniko:testcontainers-keycloak` against the Testcontainers 2.x used here; fallback: `GenericContainer` with the same image and command as compose). Run `cd backend && ./mvnw -q test -Dtest=KeycloakUsersTest` → FAIL, then implement, → PASS.
- [ ] Step 3: Measure and note the added first-start time in the commit body. Commit `test(build): real Keycloak container and token helper`.

### Task 4: Account on an external identity (domain, migration, removals)
**Files:** Modify `Account.java`, `DisplayName.java`, `AccountFixture.java`, `AccountTest.java`, `pom.xml` (drop `spring-security-crypto`); Delete `AuthSession`, `AuthSessionRepository`, `Hasher`, `PasswordHash`, `RawPassword`, `BcryptHasher`, `StubHasher`, `LoginSucceeded`, `LoginFailed`, `LoginCommand`, `RegisterCommand`, `AuthResponse`, `LoginRequest`, `RegisterRequest`, `SessionIdentityFilter`; Create `V105__external_identity.sql`.
**Interfaces — Produces:**
`public static Account.Registration Account.registerFromIdentityProvider(UserId id, Email email, DisplayName displayName, Instant now)` (ACTIVE, event `AccountRegistered(userId, email, now)`);
`public static DisplayName DisplayName.derive(String name, String preferredUsername, String email)` (C7).
`Account` loses `passwordHash`, `failedLoginAttempts`, `lastLoginAt`, `login(...)`, `register(...)`.
Migration: drops `auth_session`, drops the three columns, deletes all accounts and every row that references them (read V2xx to V4xx for the foreign keys; old accounts have no Keycloak twin, D6).

- [ ] Step 1: Failing tests in `AccountTest`: `registerFromIdentityProviderKeepsTheGivenIdAndStartsActive`, `registerFromIdentityProviderAnnouncesAccountRegistered`; in `ValueObjectsTest` a `@ParameterizedTest` `deriveFallsBackFromNameToUsernameToEmailPrefix` (`("Alice A","alice","a@x.org")→"Alice A"`, `("  ","alice","a@x.org")→"alice"`, `("","","a@x.org")→"a"`) and `deriveCutsALongNameToTheLimit`.
- [ ] Step 2: Implement; delete the listed classes and the tests that covered them (`./mvnw clean ...` afterwards, stale classes). `AccountFixture.build()` uses the new factory and `signUp`'s body is replaced in Task 5; keep every other method and the `signUp(MockMvc, JsonMapper, String)` signature so the 12 other test classes need no change.
- [ ] Step 3: `./mvnw -q clean test -Dtest='AccountTest,ValueObjectsTest'` → PASS (the rest of the build is red until Task 5; do not commit a red build: work on Tasks 4 and 5 and commit them together if the push gate would fail, otherwise commit now with only domain classes changed).
- [ ] Step 4: Commit `refactor(account): account without passwords, created from the identity provider`.

### Task 5: Resource server and account creation on first login
**Files:** Create `IdentityClaims.java`, `SecurityConfig.java`, `JwtCallerConverter.java`, `AccountServiceTest` cases, `AccountFirstLoginTest.java` (web package or application, integration); Modify `AccountService.java`, `CurrentUserImpl.java`, `AccountController.java` (only `/me` stays), `AccountControllerTest.java`, `AccountFixture.signUp`, `application.yml`, `pom.xml` (resource server starter).
**Interfaces:**
- Consumes: `Account.registerFromIdentityProvider`, `DisplayName.derive`, `KeycloakUsers.create`.
- Produces: `record IdentityClaims(String subject, String email, String name, String preferredUsername)` (package-private to the account module);
  `@Transactional public UserProfile AccountService.resolveOrCreate(IdentityClaims claims)` — loads by `UUID.fromString(subject)`; absent → creates, saves with flush, publishes `AccountRegistered`; unique violation → re-read, no event; inactive, unparsable `sub`, blank `email`, or an email owned by another id → `DomainException(UNAUTHENTICATED)`, nothing saved or published.
  `AccountFixture.signUp(MockMvc, JsonMapper, String displayName)` now: `KeycloakUsers.create`, then `GET /me` with the bearer token (creates the account), returns the fixture with id = `sub`, email, token. Same return type as before.
  `CurrentUserImpl.get()` reads the `UserProfile` from the Spring Security `Authentication` principal; throws `UNAUTHENTICATED` when there is none (also outside a request).
  Security: stateless, CSRF off (bearer header, no cookies on this API), everything authenticated except `/actuator/**`; entry point writes the existing problem+json 401 body (code `UNAUTHENTICATED`); `JwtCallerConverter` builds `IdentityClaims` from the JWT and calls `resolveOrCreate`.

- [ ] Step 1: Failing service tests (Mockito): `unknownSubjectCreatesTheAccountAndPublishesAccountRegistered`, `knownSubjectLoadsTheAccountAndPublishesNothing`.
- [ ] Step 2: Failing integration test (F1) `AccountFirstLoginTest.twoFirstRequestsAtOnceCreateOneAccountAndOneEvent`: two threads call `GET /me` with the same new token; then exactly one row in `accounts` for that `sub` and exactly one `AccountRegistered` for that id in `ApplicationEvents`. Also `meReturnsTheProfileWithoutAToken401` replaced by `meWithoutATokenIs401Unauthenticated`.
- [ ] Step 3: Implement; `AccountControllerTest` keeps only `/me` cases. Remove `EMAIL_TAKEN` and `INVALID_CREDENTIALS` from `ErrorCode.java` (the contract lost them in Task 1; the frontend table follows in Task 6). Run `./mvnw -q clean verify` → all green (this proves the 12 other integration test classes still pass through the new `signUp`).
- [ ] Step 4: Commit `feat(account): validate Keycloak tokens and create the account on first login`. Update `backend/CLAUDE.md` and conventions 01/02/03 mentions of sessions/passwords/`/auth/register` in the same commit (grep `auth/register`, `signUp`, `BCrypt`, `session`).

### Task 6: Frontend OIDC sign-in, callback, logout
**Files:** Create `src/lib/oidc.ts`, `src/app/api/auth/callback/route.ts` and tests beside each; Modify `src/lib/session.ts`, `src/lib/user-cookie.ts`, `src/app/api/auth/login/route.ts`, `src/app/api/auth/logout/route.ts`, `src/app/login/page.tsx`, `.env.example`; Delete `src/app/register/`, `src/app/api/auth/register/`, the register mode of `AuthForm`, `src/lib/errors.ts` entries `EMAIL_TAKEN`/`INVALID_CREDENTIALS`; regenerate `src/api/schema.d.ts` with `npm run gen:api`.
**Interfaces — Produces:**
`oidc.ts`: `authorizationUrl(opts: { state: string; challenge: string; register: boolean }): string` (register → Keycloak's registrations endpoint); `exchangeCode(code: string, verifier: string): Promise<Tokens>`; `refresh(refreshToken: string): Promise<Tokens>`; `endSessionUrl(): string`; `type Tokens = { accessToken: string; refreshToken: string; expiresAt: number }`.
`session.ts`: `sessionCookie(t: Tokens): string`, `readSession(req: Request): Tokens | undefined`, `clearedSessionCookie(): string`, `safeReturnTo(value: string | null): string` (only a path starting with one `/`, else `/`).
Env: `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID=sharewise-web`, `APP_URL=http://localhost:3000`.
Handlers: `GET /api/auth/login?returnTo=&register=1` sets a short-lived httpOnly cookie with `state`, PKCE verifier and `returnTo`, redirects to Keycloak; `GET /api/auth/callback` checks `state`, exchanges the code, sets the session cookie, calls backend `GET /me` (creates the account, D4), redirects to `returnTo`; `POST /api/auth/logout` clears the cookie and redirects to `endSessionUrl()`. An unreachable Keycloak shows a plain "Sign-in is unavailable" page.

- [ ] Step 1: Failing unit tests: `safeReturnToAcceptsOnlyRelativePaths` (`/groups`→itself; `//evil.com`, `https://evil.com`, `/\evil.com`, `null`→`/`), `callbackWithAWrongStateSetsNoSessionCookie`, `callbackWithAValidCodeSetsTheCookieAndCallsMe` (mock `fetch`), `logoutIsPostOnlyAndClearsTheCookie`.
- [ ] Step 2: Implement hand-written code flow (no library needed; if you pick Auth.js or `openid-client`, keep the Global Constraints). `npm test` → PASS.
- [ ] Step 3: `npm run gen:api` (guard blocks hand edits), `npm run lint && npm run typecheck && npm test && npm run build` → green. Commit `feat(frontend): sign in with Keycloak (code flow with PKCE, httpOnly cookie)`.

### Task 7: Forwarding with Origin check and refresh
**Files:** Modify `src/lib/backend.ts`, `src/lib/backend.test.ts`.
**Interfaces — Consumes:** `readSession`, `refresh`, `sessionCookie`. **Produces:** `proxyToBackend(req, backendPath)` unchanged signature. New behaviour: non-GET/HEAD requests whose `Origin` header is missing or differs from `APP_URL` answer 403 problem+json with the frontend-only `code: "FORBIDDEN_ORIGIN"` (same precedent as `BACKEND_UNREACHABLE` in `backend.ts`; the backend contract does not change) **without calling the backend**; an access token within 30 s of expiry is refreshed first, one refresh at a time per refresh token (parallel callers await the same promise); a failed refresh clears the cookie and answers 401 `UNAUTHENTICATED`; the new tokens are set on the response.

- [ ] Step 1: Failing test (F2) `postFromAnotherOriginIsRejectedAndTheBackendIsNotCalled` (fetch mock never called, status 403) and `postFromTheSameOriginIsForwardedWithTheBearerToken`.
- [ ] Step 2: Implement (single-flight refresh included, no extra test per scope decision). `npm test` → PASS. Commit `feat(frontend): Origin check and token refresh in the backend proxy`.

### Task 8: End-to-end journey through Keycloak
**Files:** Modify `frontend/e2e/mallorca.spec.ts`, `frontend/playwright.config.ts` if env is needed.
- [ ] Step 1: The journey registers a fresh user on Keycloak's registration page each run (unique email, no seeded users), lands back signed in, then continues as before.
- [ ] Step 2: With `docker compose up -d db keycloak`, the backend and `npm run dev` running: `npm run e2e` → PASS. While the browser is open check once by hand that the session cookie is set and the login does not loop (F4).
- [ ] Step 3: Commit `test(e2e): journey signs up through Keycloak`.

### Task 9: Documentation and final gates
**Files:** Modify `RUNNING.md`, `docs/conventions/01..04` (any rule about login, sessions, signUp), `docs/diagram-deltas.md`, `docs/devsetup/CATALOG.md` if it mentions login, `README.md`.
- [ ] Step 0: Rewrite what the conventions say about the old login: `01_ARCHITECTURE_AND_NAMING.md` section 2.4 (`PasswordHash`, `RawPassword` leave the value-object table), section 3.4 (`Account` has no `passwordHash`/`lastLoginAt`/`login`, new `registerFromIdentityProvider`, events no longer `LoginSucceeded`/`LoginFailed`, `Hasher` gone) and the `SessionIdentityFilter` example in section 4; `03_CODE_STYLE.md` section 4 (no passwords or BCrypt in ShareWise; every endpoint except `/actuator/**` needs a valid Keycloak token; tokens only in the httpOnly cookie of the Next.js server).
- [ ] Step 1: `RUNNING.md`: start Keycloak (`docker compose up -d db keycloak`), the admin console URL and local credentials, the three frontend env vars, "after changing the realm file or wiping one database wipe both: `docker compose down -v`" (F3, F16). `02_TESTING.md` section 3: `signUp` now creates a Keycloak user; the "no shortcuts" rule stays. `diagram-deltas.md`: Account now `registerFromIdentityProvider`, no `login`.
- [ ] Step 2: Grep the repo (not `archive/`) for `auth/register`, `auth/login`, `BCrypt`, `password`, `INVALID_CREDENTIALS`, `EMAIL_TAKEN`, `sw_session` and fix leftovers.
- [ ] Step 3: Run the full set: `cd backend && ./mvnw -q clean verify`; `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`; `npx @redocly/cli lint docs/api/openapi.yaml` → all green.
- [ ] Step 4: Commit `docs(account): running, conventions and diagram deltas for Keycloak`. Then `/review-branch`, and merge with `--no-ff`.

**Not part of this plan:** the move of the domain diagram to `docs/domain/` (own `chore/` branch), the settlement and remaining features (next brainstorming round).
