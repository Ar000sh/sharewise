# Lab deployment validation, 18 November 2026

Prepared the frontend Dockerfile and compose.deploy.yml for all four services,
with separate persistent volumes, loopback-only frontend/Keycloak ports and an
optional internal issuer address for server-side OIDC calls. See DEPLOYING.md.
No registry publication, commit, push or university VM deployment was performed.

## Observed results

- Compose interpolation and configuration validation passed with private values.
  The blank example correctly fails required-password validation.
- Both local images exist. The frontend runtime started successfully and
  http://localhost:3100/login returned HTTP 200.
- PostgreSQL became healthy; Spring Boot and Keycloak completed their initial
  startup and the realm imported successfully. A later restart with service
  memory limits did not finish warming up during the remaining checks.
- Lint, route type generation and TypeScript checking completed without reported
  errors in the initial host check sequence.
- The initial Vitest run failed to start workers. The single-worker retry also
  stalled. No passing unit-test result is claimed.
- A subsequent image rebuild compiled successfully but was cancelled while
  completing later steps. The Linux checks-image export stalled and was cancelled.
- Automated browser verification stalled at startup and was cancelled. Complete
  registration, login, refresh, authenticated API and logout remain unverified.
- A frontend-container request to the backend health endpoint timed out under
  load. Runtime integration is therefore not yet confirmed.
- Git whitespace checks passed with CRLF-aware settings.

Windows reported approximately 241 MB free physical memory out of 16 GB during
the stalled checks. Other unrelated Docker projects were running. The ShareWise
lab stack was stopped to release its resources, keeping its volumes and images.
Re-run the commands in DEPLOYING.md when memory is available; do not interpret
the successful frontend page response as proof that authentication works.

## Implementation choices (call)

### Follow-up checks on 18 November

After Docker Desktop became available, all four services started. The frontend
login page and Keycloak discovery endpoint returned HTTP 200, the login endpoint
returned a 302 redirect, and a request from the frontend container to the backend
health endpoint returned HTTP 200 with status UP. Full browser authentication is
still a separate check. The earlier timeout observations above describe the
initial resource-constrained run, not the later successful health checks.

The GitHub container workflow passed actionlint 1.7.7. It tests and builds on
branches and pull requests and publishes commit-tagged images on the default
branch only. Publication and VM deployment require a successful remote run.

- Frontend port 3100 preserves the demo on 3000 and matches the existing realm.
- Keycloak retains the existing lab start-dev/H2 approach with a separate volume.
- Production Next.js runs as the node user; local secrets are generated in the
  gitignored .env.deploy.local and were not printed.
- Service memory limits are 512 MB frontend, 768 MB backend, 768 MB Keycloak,
  and 256 MB PostgreSQL. These do not reserve capacity for image builds.
