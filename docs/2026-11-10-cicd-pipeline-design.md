# CI/CD pipeline: design

Status: draft, brainstormed 10.11.26. Branch `feature/cicd-pipeline`.

## Intent

ShareWise moves along the semester path modular monolith → containers → **CI/CD** → cloud → microservices. This step
adds a production-style pipeline: every change is built, checked and tested on a server nobody can skip, and every
green build of `main` produces Docker images in a registry, ready for the cloud deploy that comes next.

The idea to defend in P3: **build once, test the artifact you ship, promote that same artifact.** The images the
end-to-end test runs against are byte for byte the images pushed to the registry and, later, deployed.

The professor gave no stage list for the pipeline; the stages below are our choice.

## Goals
- One GitHub Actions pipeline that runs on every pull request and every push to `main`.
- Stages: checks (format, lint, typecheck, contract lint) → tests (backend verify with Testcontainers, frontend
  vitest) → Docker images for backend and frontend → Playwright e2e against those images → push to `ghcr.io` (only on
  `main`).
- `main` is protected: changes arrive only through pull requests, and a PR can be merged only when the pipeline is
  green.
- Pipeline failures are diagnosable without rerunning locally (Playwright traces and screenshots, test reports kept as
  build artifacts).

## Non-goals (for now)
- **Deploying to the cloud.** There is no cloud target yet. The pipeline ends with images in the registry; a deploy
  stage is added when the cloud setup exists (its own design).
- Separate pipelines per app. Backend and frontend share one contract (`docs/api/openapi.yaml`) and the e2e stage
  needs both images, so they stay in one pipeline. Splitting per service belongs to the microservices step, where the
  pipeline layout can follow the new architecture.
- Path filters that skip the backend or frontend jobs. `main` must be fully green, and a skipped job is a check that
  did not run.
- Release versioning (semver tags, changelogs), security scanning, performance tests.

## Anti-goals
- A second list of checks that drifts from the local gate. The checks live in one place (see "Decided").
- Images that differ between environments (built with environment-specific values baked in).
- Secrets in images or in the repository. The repo and its images are public.

## Current state (10.11.26)
- No `.github/` folder, no CI.
- No Dockerfiles for the apps. `docker-compose.yml` starts only Postgres (`db`) and Keycloak (`keycloak`, realm
  imported from `docker/keycloak/`, `KC_HOSTNAME=http://localhost:8180`).
- `.githooks/gate.mjs pre-push` runs: backend `./mvnw -q verify` (Docker), frontend lint, typecheck, test, build,
  contract lint (`@redocly/cli`), guard hook tests. It skips docs-only pushes. It does **not** run the Playwright e2e.
- Frontend config is read on the server at runtime: `BACKEND_URL`, `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `APP_URL`
  (no `NEXT_PUBLIC_*`). Backend config: datasource URL and `issuer-uri` in `application.yml`.
- `frontend/playwright.config.ts` always starts its own dev server on port 3100 (the port the realm allows as
  redirect URI).
- Repo `Ar000sh/sharewise` is public on GitHub.

## Decided (with the user)
1. **GitHub Actions + GitHub Container Registry (`ghcr.io`).** Repo is on GitHub and public: no extra accounts, the
   built-in `GITHUB_TOKEN` can push images, minutes and storage are free.
2. **One pipeline, parallel jobs, fail fast.** Backend, frontend and contract checks run side by side; no image is
   built before all of them are green; the two images are built in two jobs at the same time; the e2e stage needs
   both images; the registry push waits for the e2e stage.
   ```
   PR / push ─┬─ backend:  format check → verify (unit + Testcontainers) ─┐
              ├─ frontend: lint → typecheck → vitest → build ─────────────┼─┬─ image-backend ──┐
              └─ repo:     redocly lint, guard hook tests ────────────────┘ └─ image-frontend ─┴─ e2e on the images ─ push to ghcr (main only)
   ```
   *Changed 11.11.26 after the first CI run:* the first version built both images in one job, one after the other
   (90 s and 174 s), so the job took about 5 min. They are now one job each and build at the same time. The images
   still wait for every check (the user's decision: fail fast, a commit with a red check costs no image build). We
   considered starting the builds next to the checks, which would have saved about 4 minutes; the cost of the chosen
   order is those minutes, the gain is that a red commit wastes no runner time on images.
3. **More than the local gate.** CI runs everything the pre-push gate runs **plus** the image build and the e2e test
   against the built images.
4. **One image per app, configured at runtime.** The same image runs in CI, locally and later in the cloud; only
   environment variables differ.
5. **Tags:** every image gets the full commit SHA as its tag; builds of `main` also get `latest`. Images are pushed
   only from `main` and only after the e2e stage passed. PR builds build and test the images but do not push them.
6. **`main` is protected; work arrives by pull request.** Branch protection on `main` (the user owns `Ar000sh` and
   sets it up):
   - required status check: the pipeline's final job; no direct pushes;
   - **applies to administrators too** ("do not allow bypassing"), so the owner cannot skip it either;
   - **no required review approval**: green CI is the only merge condition;
   - **branches need not be up to date with `main` before merging.** Accepted trade-off: two PRs that are green on
     their own can merge one after the other and leave `main` red. The pipeline also runs on every push to `main`, so
     that shows up at once, and no image is pushed from a red `main`.
   - merge method "Create a merge commit" only (same history shape as today's `--no-ff`; squash and rebase merges
     disabled).
7. **Who opens and merges PRs.** Claude opens PRs with `gh pr create`. Both the user (GitHub UI) and Claude
   (`gh pr merge --merge`) may merge a green PR. The GitHub CLI `gh` becomes a required tool (`RUNNING.md`).
8. **CI never skips.** Unlike the local gate, CI runs the full pipeline for docs-only changes too, including the image
   build and push on `main`, so every `main` commit has images tagged with its SHA (the later deploy stage relies on
   that).
9. **Local hooks stay.** pre-commit and pre-push give fast feedback before pushing; CI is the check that cannot be
   skipped.
10. **Docs and skills change with it.** `CLAUDE.md` ("merged with `--no-ff`", workflow table) and
   `docs/conventions/03_CODE_STYLE.md` (Branches rule) describe the PR flow; `RUNNING.md` gets `gh`, how to build and
   run the images locally and how to run the e2e against them; the conventions README's enforcement table gets a
   "CI pipeline" row. The skills that merge locally or assume a local `main` (`.claude/skills/review-branch` step 6,
   `review-doc`, `hand-in`) switch to the PR flow.

## Left to the builder (guidance, not decisions)
- **One list of checks.** Prefer CI calling the same commands as `gate.mjs` (or the gate exposing them) over copying
  them into YAML. The docs-only skip of the local gate does not apply in CI (decision 8).
- **Moving the tested image.** The image the e2e job tested must be the one pushed (e.g. `docker save` as a build
  artifact, loaded in the push job), not a rebuild.
- **Dockerfiles.** Backend: multi-stage (Maven build → JRE runtime, non-root user). Frontend: multi-stage with Next.js
  `output: "standalone"` (needs a `next.config.ts` change). Each with a `.dockerignore`. Health endpoints for
  start-up waiting (backend: Actuator health if present, otherwise add it; frontend: `/login` answers).
- **e2e wiring.** Start `db` and `keycloak` from the existing compose file plus the two built images (a compose
  override file for CI is the obvious shape). The frontend runs on port 3100 so the realm needs no change.
  `playwright.config.ts` gets a switch: when a base URL is given (e.g. `E2E_BASE_URL`), skip `webServer` and test that
  URL; without it, local behaviour is unchanged.
- **Keycloak issuer trap.** The issuer must be the same URL for the browser, the frontend server and the backend
  (`KC_HOSTNAME=http://localhost:8180`). Inside a Docker network `localhost` differs per container. Simplest on the
  Linux runner: run the app containers with host networking so `localhost:8180`, `:8080` and `:5432` mean the same
  everywhere. The cloud will need its own answer; not in scope here.
- Caching (Maven repository, npm, Docker layers) to keep the pipeline reasonably fast.
- Image names: `ghcr.io/ar000sh/sharewise-backend` and `ghcr.io/ar000sh/sharewise-frontend` (ghcr names must be
  lower case).
- Concurrency: a newer push to the same PR cancels the older run.

## Failure modes
To be filled by `/edge-case-probe` plan mode.

## How we will know it works
- A PR with a broken unit test, a lint error, a broken Dockerfile and a broken e2e flow each turns the PR red and
  blocks the merge (show one in P3).
- A merged PR leaves two images on ghcr.io tagged with the merge commit's SHA and `latest`.
- `docker run` of those images with local env vars, plus `docker compose up -d db keycloak`, gives a working
  ShareWise on `localhost:3100`.
