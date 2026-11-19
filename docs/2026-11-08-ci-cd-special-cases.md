# CI/CD special cases and coverage

Snapshot: 08 November 2026. Related handoff: [progress and requirements review](2026-11-08-progress-and-handoff.md).

## User report and evidence boundary

The user reports: "The implementation of CI/CD did not include some 'special cases'." The user has now specified the first priorities: **environment variables and secrets, database migrations, and realistic failures that can interrupt or crash the application**. The relevant pipeline/branch has not yet been identified. These priorities are confirmed; pipeline coverage and reproduced outage behavior remain to be verified.

At the initial review there was no pipeline definition or application Dockerfile. A later [create-group runtime fix](2026-11-08-create-group-runtime-fix.md) added a backend Dockerfile and an optional Compose `app` profile alongside PostgreSQL and Keycloak. The frontend still runs on the host, and no `.gitlab-ci.yml`, `.github/workflows` directory, Jenkinsfile or comparable pipeline definition has been added. `.githooks/gate.mjs` provides local checks; it is not a hosted CI/CD implementation. Repository notes describe the professor's reference GitLab pipeline, not an implementation in ShareWise.

The following eight cases were initially assessed statically. They are engineering acceptance checks, not additional requirements claimed to come from the professor. No CI pipeline has been added. The incident record now supersedes the original evidence boundary for C2 and local identity-loading recovery in C7: the wrong backend service was reproduced, runtime wiring/error handling were corrected, and real registration, sign-in, group creation and reload passed. The migration chain was applied to a fresh empty database. No populated-upgrade or deliberate outage test has been performed.

## First priority: configuration and secrets

An environment variable is a delivery mechanism for configuration. A secret is a value whose access must be restricted. Public URLs and a public OAuth client ID are configuration; database passwords, administrator passwords and bearer/refresh tokens are secrets. The current `sharewise-web` client is public and has no client secret to invent or store.

| Input | Consumer | Classification | What needs checking |
|---|---|---|---|
| `BACKEND_URL` | Next.js server | Configuration | Reachable from the server, valid HTTP(S) URL, not accidentally the container's own loopback address |
| `APP_URL` | Next.js server | Public configuration | Browser-facing origin; callbacks, logout, and Origin checks agree |
| `KEYCLOAK_ISSUER` | Next.js server | Public configuration | Canonical realm URL agrees with the token issuer and the backend |
| `KEYCLOAK_CLIENT_ID` | Next.js server | Public configuration | Existing public client, expected redirect allowlist and PKCE settings |
| `SPRING_DATASOURCE_URL` | Spring Boot | Configuration; treat any embedded credentials as secret | Correct PostgreSQL target; do not embed a password in a URL that could be logged |
| `SPRING_DATASOURCE_USERNAME` | Spring Boot | Restricted deployment configuration | Correct database role for the target |
| `SPRING_DATASOURCE_PASSWORD` | Spring Boot | Secret | Supplied through the deployment environment's secret mechanism; no production fallback to the checked-in local-development value |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Spring Boot | Public configuration | Spring's environment override for the JWT issuer; setting the frontend's `KEYCLOAK_ISSUER` alone does not set this property |
| `KC_HOSTNAME` | Keycloak | Public configuration | Advertised origin is correct for the target and reachable by the browser |
| `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` | Keycloak bootstrap | Restricted configuration / secret | Separate local defaults from deployment values; do not expose administrator credentials to frontend code |
| Access/refresh tokens | Browser cookie and Next.js server | Secrets | Never log or publish them; preserve HttpOnly handling and define cookie flags for an HTTPS deployment |

The Spring environment names above are the intended standard overrides for the existing properties, not evidence that a deployment already supplies them. Compose currently assigns literal local-development values: merely exporting a host variable of the same name does not replace a literal YAML value unless that wiring is changed.

The ignore-pattern check confirmed that `frontend/.gitignore` covers `frontend/.env.local`, `.env` and `.env.production`, and deliberately permits `.env.example`. The root ignore file only covers `.env*.local`; test paths `backend/.env` and `backend/.env.production` did not match an ignore rule. The local commit gate rejects environment files when enabled, but ignore rules and optional local hooks are not a remote secret-management policy. There is no claim here that a real secret has leaked.

Configuration acceptance should include missing, empty and malformed inputs. In particular, `??` defaults do not replace an empty string. Invalid deployment configuration should fail a controlled preflight with variable names and no secret values, rather than appear as a broken login or an unexplained request failure. A valid configuration whose dependency is temporarily unavailable requires a different runtime response.

## Cases to reconcile with the pipeline

| ID | Case and repository evidence | What is already handled locally | CI/CD acceptance still to prove |
|---|---|---|---|
| C1 | **High: configuration or secret delivery is wrong.** URLs use local defaults; deployment validation is not centralized; some URL parsing occurs outside a request's error handler. | Local examples and HttpOnly cookies exist. `.env.local` is ignored and the local commit gate blocks environment files when enabled. | Test missing/empty/malformed required configuration; prove deployment values and secret injection work without a developer's local files; report names without printing secret values. A malformed `APP_URL`, for example, can throw in `isSameOrigin()` before the backend proxy's `try` block. |
| C2 | **High: public and internal addresses disagree.** A container's `localhost` is its own loopback; OAuth and CSRF checks use a public origin. | Host-based issuer defaults are consistent; Playwright supplies `APP_URL` for port 3100. | Verify the canonical issuer from browser, Next.js and backend; ensure redirect/logout allowlists and Origin checks match the target. Do not substitute an internal hostname as a browser URL without proving reachability. |
| C3 | **High: destructive migration is applied to retained data.** `account/V105__external_identity.sql` deletes account-related records and publications and removes custom-auth tables/columns. | The deletion was explicitly approved for disposable development data and is documented as such. | Establish the target's data policy before applying historical migrations. A data-preserving deployment needs a migration/reconciliation plan, not an automatic reset. Do not edit already-applied migrations, use `down -v` as deployment recovery, or run Flyway repair merely to conceal a mismatch. |
| C4 | **High: an upgrade assumes empty tables.** `account/V106__account_username.sql` adds a non-null username without backfill; `group/V205__invitations_for_accounts.sql` adds a non-null invitee without backfill. | Comments explicitly require recreating the development data. Fresh installation runs earlier migrations before users exist. | Test a representative populated pre-change database separately from a fresh one. Existing rows cannot acquire a non-null value from these statements; the upgrade path is not safe merely because fresh Testcontainers tests pass. New-version migrations cannot repair a failure in an earlier migration that prevents reaching them. |
| C5 | **High: a backend or database becomes unavailable or too slow.** The proxy catches connection errors but its backend fetch has no application-defined abort deadline. Database startup and later connectivity are separate situations. | Failed backend fetches become a 502 problem; expense save and event publication share a transaction. | Prove bounded responses to a stalled backend, clean failure/recovery when the database is unavailable, and no half-committed expense/publication. An aborted client response does not prove a write was rolled back: do not blindly retry state-changing requests. |
| C6 | **Medium: temporary Keycloak failure is treated as an expired session.** The refresh handler catches every error, returns 401 and clears the session. | Keycloak calls have a five-second bound; an actual rejected grant has a distinct `TokenRejectedError`; parallel refreshes are shared within one Next.js process. | Distinguish revoked/expired refresh credentials from timeouts and provider failures. Test that temporary outage has a retryable explanation and that invalid credentials still clear the session. Single-process refresh coalescing is not proof of behavior across several frontend instances. |
| C7 | **Medium: identity loading fails. Local recovery now implemented.** Previously non-401 `/me` errors were discarded. | The runtime follow-up added a visible account-loading error, retry action and regression test that protected content is hidden on failure. Initial 401 identity checks still redirect to login. | Include the recovery check in CI, and separately verify the intended handling of later 401 responses; `api()` does not centrally redirect them. Full network fault injection remains unperformed. |
| C8 | **High for correctness: the process survives but a background booking fails.** A failed listener leaves an incomplete publication and stale balances. | Persisted publications, idempotent ledger booking and replay on restart exist; ordinary lag has bounded UI refreshes. | Demonstrate failed-then-replayed booking without duplication, and make operational failure distinguishable from normal lag. Decide whether restart-only recovery remains acceptable; do not silently implement a new scheduler. A healthy web endpoint alone does not prove that balances are up to date. |

## Second priority: migration acceptance

Treat **fresh installation**, **populated upgrade** and **recovery** as three distinct checks:

1. Fresh installation: apply the complete migration chain to an isolated empty PostgreSQL database, start the application, and run the real account/group/expense/balance journey.
2. Populated upgrade: create an isolated database at the relevant earlier schema, insert representative data, then apply the candidate version. Establish which identity and invitation records require transformation before judging the result. Current V106/V205 intentionally do not backfill them.
3. Recovery: interrupt or fail a candidate upgrade only in an isolated database, inspect its resulting migration/schema state, and prove the chosen recovery procedure. A previous application image may no longer match the new schema, so an application rollback is not automatically a database rollback.

Keycloak identity data and ShareWise account data also need a coordinated policy: recreating Keycloak can give the same person a new subject ID while existing ShareWise rows still refer to the old one. No live data should be wiped to perform these checks.

## Third priority: distinguish kinds of failure

| Kind | Example here | What a useful check establishes |
|---|---|---|
| Startup prevented | Database unreachable or migration fails | Startup/deployment fails clearly, preserves data under the agreed policy, and does not report the release as healthy |
| One request fails | Invalid configuration, rejected token or dependency error | A bounded response with the appropriate explanation; do not claim a Node/JVM process crash solely from a request error |
| UI gets stuck | Non-401 `/me` failure | User sees the failure and can retry rather than waiting forever |
| Silent inconsistency | Listener failure after the expense commits | The publication remains recoverable, replay is idempotent, and stale financial state is detectable |

No CPU/memory exhaustion, traffic surge or multi-node failure was reproduced in this review. Broad stress-test infrastructure is not an assumed requirement for the current small application.

## Coverage levels

- **Build gate:** backend verification; frontend lint, type checking, tests and production build; API contract lint; relevant hook tests.
- **Integration gate:** real Keycloak/PostgreSQL readiness, a complete browser journey and asynchronous results awaited with a bound.
- **Deployment gate:** selected image/artifact, target URLs and runtime configuration, migration impact and a smoke check of the deployed application.

Runner basics remain part of those gates: Java 21, the documented Node 22+ setup, lockfile-based dependency installation, Docker for real Keycloak/PostgreSQL tests, readiness waits, Playwright browser availability, and explicit branch/merge-request triggers. Local Git-hook input and Markdown-only skip logic must not be copied into CI without adapting its change-detection context. The observed local Windows fork-worker timeout is not evidence that tests should be skipped remotely.

Course notes distinguish Ex7 (build pipelines) from Ex8 (deployment adaptation). Do not silently make every deployment concern a condition of completing Ex7; track its stage explicitly.

## Open decisions

- The first priorities are now confirmed above. Which CI/CD implementation or branch should these be checked against?
- Which CI provider and runner type will be used? The professor's reference uses GitLab; that is evidence about the reference, not a confirmed team decision.
- Which domain features and integration journeys are required for the current hand-in?
- What is the deployment target's public URL, and how will all services reach the canonical Keycloak issuer?
- What data must be preserved on deployment, and which migration/rollback strategy is acceptable for that environment?

## Sources

- [Recorded course requirements and exercise stages](01_Requirements_from_Prof.md)
- [Reference template analysis](02_Reference_Template_Takeaways.md)
- [Local Git gates](../.githooks/gate.mjs)
- [Compose services](../docker-compose.yml)
- [Backend Testcontainers setup](../backend/src/test/java/de/hsh/favs/sharewise/TestcontainersConfiguration.java)
- [Frontend environment example](../frontend/.env.example)
- [Frontend proxy, origin checks and token refresh](../frontend/src/lib/backend.ts)
- [Playwright configuration](../frontend/playwright.config.ts)
- [Migration conventions](conventions/01_ARCHITECTURE_AND_NAMING.md#6-persistence-and-database)
- [Current event delivery limitations](2026-10-27-event-delivery-and-messaging.md)
- [Destructive identity migration V105](../backend/src/main/resources/db/migration/account/V105__external_identity.sql)
- [Username migration V106](../backend/src/main/resources/db/migration/account/V106__account_username.sql)
- [Invitation migration V205](../backend/src/main/resources/db/migration/group/V205__invitations_for_accounts.sql)
- [Current-user loading behavior](../frontend/src/lib/currentUser.tsx)
- [OIDC calls and rejected-grant distinction](../frontend/src/lib/oidc.ts)
- [Session cookie and Origin handling](../frontend/src/lib/session.ts)
