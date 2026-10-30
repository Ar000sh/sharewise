# Keycloak authentication (replaces the built-in login)

**Date:** 30.10.26 · **Status:** draft, waiting for approval · **Origin:** brainstorming with the user; the diagram variant is `work/proposals/ShareWise_Domain_Model_Account_External_IdP.drawio`

Marked *(call)* = chosen by Claude without asking, can be overruled. Everything else was decided by the user.

## 1. Intent
Phase one of the project is the complete, working application. Authentication is part of it and is done by a real identity provider (Keycloak), not by our own code. ShareWise never sees or stores a password; registration, login, password reset and token issuing belong to Keycloak. This is the "Account context with external identity provider" of the proposal diagram and practises: generic subdomain bought instead of built, OAuth2/OIDC, eventual consistency between two systems (no shared transaction).

Later phases (containers, CI/CD, cloud, microservices) are not part of this document. The settlement feature and the other missing features of the diagram and R1 to R17 are separate brainstorming rounds that follow this one.

## 2. Goals, non-goals, anti-goals
**Goals**
- A user signs up and signs in on Keycloak's own login page and returns to ShareWise signed in.
- The backend accepts only valid Keycloak access tokens (JWT) and knows the caller as a `UserId`.
- An `Account` exists in ShareWise for every person who has signed in, created automatically on first login.
- `docker compose up` gives a working Keycloak with a ready realm on a fresh clone.
- Backend integration tests run against a real Keycloak (Testcontainers), keeping the rule "no shortcuts around authentication".

**Non-goals**
- Email verification (off in phase one), social login, roles/permissions inside Keycloak, multi-factor, custom Keycloak theme.
- Migrating existing accounts: all current accounts are test data and are dropped.
- Changing group/expense/settlement behaviour.

**Anti-goals** (it would be a failure even if it works)
- A password or password hash anywhere in ShareWise code, logs or database.
- Tokens readable by browser JavaScript.
- A second code base deployed into Keycloak (no event listener plugin).
- Test-only back doors in production code (a "trust this header" filter, a dev profile with seeded users).

## 3. Decisions
| # | Decision | Why |
|---|---|---|
| D1 | Sign-in uses **Keycloak's own login page** (OIDC authorization code flow with PKCE). Our own login/register forms are not built. *(user)* | Least code, the credentials never touch ShareWise, easy to defend in P2/P3. Cost: the login screen is Keycloak's look. |
| D2 | **Next.js server is the OIDC client (BFF).** It starts the redirect, handles the callback, stores the tokens in an httpOnly cookie, refreshes them, and forwards the access token to the backend. *(call)* | The frontend already puts route handlers between browser and backend with a cookie; the tokens never reach browser JavaScript. |
| D3 | **Backend is an OAuth2 resource server** (`spring-boot-starter-oauth2-resource-server`): it validates signature, issuer, expiry of the JWT and issues no tokens. `sub` becomes the `UserId`. *(call)* | Standard, small, nothing of our own to get wrong. |
| D4 | **The `Account` is created on first login**, from the token's `sub`, `email` and name claim (`Account.registerFromIdentityProvider`). The Next.js callback calls `GET /me` right after login, and the backend also creates the account on the first authenticated request of an unknown `sub`, so no path skips it. *(user: "save on login"; mechanism = call)* | Keycloak cannot call us at registration without a plugin; creating on first use heals itself (the next request retries) and leaves no orphan if someone registers and never returns. It is eventual consistency between two systems. |
| D5 | **Email verification off** in the realm. *(user)* | Keeps the demo fast. Can be switched on later with an SMTP server (Mailpit). |
| D6 | **Existing accounts are dropped**, no migration. *(user)* | All test data. A new migration drops the credential/session tables and the password columns. |
| D7 | **Backend integration tests use a real Keycloak via Testcontainers**, shared like the Postgres container; `AccountFixture.signUp` obtains a real token. *(user)* | Keeps "no shortcuts around authentication" true and tests the realm file and the claim mapping. Cost: one more container and roughly 30 to 60 seconds on first start. |
| D8 | **Keycloak runs in `docker-compose.yml`** next to Postgres with a realm import file checked into the repo (self-registration on, email verification off, public client with PKCE for the frontend). *(call)* | A fresh clone works with one command. |
| D9 | **Logout also ends the Keycloak session** (RP-initiated logout). *(call)* | Otherwise "Sign out" would sign straight back in. |
| D10 | **Display name is owned by ShareWise after creation**: taken from the token on first login only, later changeable by `changeDisplayName`, never overwritten by a later login. *(call)* | The diagram has `changeDisplayName` on `Account`; overwriting it would undo the user's edit. |

## 4. What changes
**Removed:** `POST /auth/register`, `POST /auth/login`, `AuthSession` and its repository, `SessionIdentityFilter`, `PasswordHash`, `RawPassword`, `Hasher`, `BcryptHasher`, `LoginSucceeded`, `LoginFailed`, `LoginCommand`, `RegisterCommand`, the fields `passwordHash`, `failedLoginAttempts`, `lastLoginAt`, the `spring-security-crypto` dependency, the frontend register page and `api/auth/*` handlers, `session.ts` as it is today.

**Kept:** `GET /me`, `Account` (id = Keycloak `sub`, `email`, `displayName`, `status`, `registeredAt`), `AccountStatus`, `DisplayName`, `UserProfiles` and `CurrentUser` ports (so group/expense/settlement do not change), `AccountRegistered` (still announces a new account).

**New:** `Account.registerFromIdentityProvider(UserId, Email, DisplayName)`, a JWT-to-`CurrentUser` adapter, an idempotent "get or create account" in `AccountService`, Spring Security configuration (open: `/actuator/health`; everything else authenticated), a migration dropping the old tables and columns, Keycloak service and realm file in compose, OIDC callback/login/logout route handlers in Next.js, Testcontainers Keycloak configuration, updated `AccountFixture`.

**Contract:** `/auth/register` and `/auth/login` leave `openapi.yaml` in its own first commit; the 401 problem stays `UNAUTHENTICATED`; `INVALID_CREDENTIALS` and similar login codes are removed from all three places. `npm run gen:api` regenerates `schema.d.ts` (the guard hook blocks a manual edit).

## 5. Constraints
- Sources of truth: the diagram variant above is moved to `sharewise/docs/domain/` on its own `chore/` branch before this feature merges; it replaces the current Account part of the model, and `docs/diagram-deltas.md` records the change.
- Conventions apply: contract first in its own commit, module boundaries (`ModularityTests`), `ConventionTests`, Javadoc, tests at the right level. `docs/conventions/02_TESTING.md` section 3 (sign up through `POST /auth/register`) and `01`/`03`/`04` where they mention the old login are changed in the same commits as the code.
- The concurrency rule: two simultaneous first requests of the same `sub` must give one account (unique key on the id, insert that tolerates the race).
- Time and eventual consistency rules of document 02 section 4 stay.

## 6. Failure modes
To be filled by `/edge-case-probe` plan mode (next step). Topics already known: concurrent first login, token expired mid-request, refresh token revoked or expired, Keycloak down, `sub` of a deactivated account, email claim missing or changed in Keycloak, issuer/clock skew, callback with a wrong `state`, logout while the access token is still valid.

## 7. Left to the builder
- Library choice for the OIDC client in Next.js (Auth.js, `openid-client`, or hand-written code flow); the requirement is D2 and the anti-goals.
- Names of cookies and route handlers, token lifetimes, claim names beyond `sub`/`email`, realm and client names.
- Keycloak version (current stable, pinned) and how the Testcontainers Keycloak imports the same realm file as compose.

## 8. Open items
- Check whether the proposal diagram still lists `LoginSucceeded`/`LoginFailed` events; if it does, it is a diagram defect to fix (the IdP owns login now).
- Where the realm file lives (`docker/keycloak/` is the suggestion) and whether `RUNNING.md` gets the Keycloak admin credentials for local use only.

## 9. Calls from the builder check *(all Claude's calls, can be overruled)*
| # | Question | Call |
|---|---|---|
| C1 | How do backend tests get a real token? | The test container imports the **same realm file as compose**, unchanged. `AccountFixture.signUp` then uses Keycloak's admin API (admin credentials of the test container) to create a user and enable a client with direct access grants **at test time**, so that client exists only in the test container, never in the realm file. Tests also prove the realm file (issuer, claims). The e2e tests use the real browser registration, no direct grants. |
| C2 | Email collisions and changes | Keycloak refuses duplicate emails in the realm (`duplicateEmailsAllowed: false`, set explicitly in the realm file), so a second `sub` with the same email cannot occur. The email is stored on first login and **not refreshed** afterwards (same rule as the display name, D10); a later email change in Keycloak is parked. A token without an `email` claim is answered with 401 `UNAUTHENTICATED`. `accounts.email` keeps its unique constraint. |
| C3 | Deactivated account | `deactivate()` is not reachable from the contract in phase one. A valid token of a DEACTIVATED account is answered with 401 `UNAUTHENTICATED`, checked on every request (the lookup is needed anyway to resolve the caller). No new error code. |
| C4 | Where and in which transaction is the account created? | In the account module, in a filter/converter right after the JWT is validated (the place `SessionIdentityFilter` has today). It calls one `@Transactional` `AccountService` method that loads the account by `sub` or inserts it; on a unique-key violation from a concurrent request it re-reads. `AccountRegistered` is published inside that transaction only by the request that inserted the row. `GET /me` and the ports then find the account and need no transaction of their own (pitfalls 1 and 2 of document 02). |
| C5 | Issuer and URLs | Compose uses a fixed `localhost` port and `KC_HOSTNAME` so the browser, the Next.js server and the backend see the same issuer (the apps run on the host, so no hosts-file entry). In tests the issuer comes from the container's dynamic port through test properties. |
| C6 | Frontend and e2e tests | Updating them is in scope. The Playwright journey registers a fresh user through Keycloak's registration page each run (no seeded users). Component tests mock the route handlers. |
| C7 | Which claim becomes `DisplayName`? | `name`; if empty `preferred_username`; if empty the part of the email before the `@`. The value is trimmed and cut to the `DisplayName` limit so first login cannot fail on validation. The realm requires first and last name at registration. |
| C8 | Refresh failure and logout | If the refresh fails or is revoked, the Next.js server clears the cookie and redirects to Keycloak login. Logout does not revoke access tokens already issued; short access token lifetimes (Keycloak default, 5 minutes) limit that. |
