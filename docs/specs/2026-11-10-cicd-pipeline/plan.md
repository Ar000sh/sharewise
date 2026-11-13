# CI/CD pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A GitHub Actions pipeline that checks and tests ShareWise, builds backend and frontend images, runs the
Playwright e2e against those images and pushes them to ghcr.io from `main`; `main` accepts only green pull requests.

**Architecture:** `.githooks/gate.mjs` stays the one list of checks and gains a `ci <group>` mode that the workflow
calls per parallel job. Two multi-stage Dockerfiles produce environment-free images; a compose override starts them
with host networking next to the existing `db` and `keycloak` services so `localhost` means the same to browser,
frontend and backend (Keycloak issuer). The tested image tars travel as workflow artifacts to the push job, so the
pushed image is the tested one.

**Tech Stack:** GitHub Actions, Docker (Buildx), ghcr.io, Spring Boot 4.1 / Java 21 (Temurin), Next.js 16 / Node 24,
Playwright, existing `docker-compose.yml` (Postgres 17, Keycloak 26.8.1).

**Spec:** `docs/specs/2026-11-10-cicd-pipeline/design.md`

## Global Constraints
- Branch `feature/cicd-pipeline`; commits `<type>(<scope>): <subject>`, one concern each, trailer
  `Co-Authored-By: Claude <model> <noreply@anthropic.com>`. Never `--no-verify`.
- New files: LF line endings. Edit existing files with the edit tools (the gate refuses a CRLF/LF flip).
- Image names: `ghcr.io/ar000sh/sharewise-backend`, `ghcr.io/ar000sh/sharewise-frontend` (lower case).
- Tags: full commit SHA (`${{ github.sha }}`) on every pushed image; `latest` additionally on `main`. Push only on
  `push` to `main`, only after e2e passed. Never on pull requests.
- Images contain no environment-specific values and no secrets (public repo, public images).
- CI never skips: docs-only changes run the full pipeline (unlike the local gate).
- Required status check on `main` is the single job named `ci-passed`.
- Backend: Java 21. Frontend: Node 24. Frontend e2e port 3100 (the realm allows it); `APP_URL=http://localhost:3100`.
- Docs change in the same branch: `CLAUDE.md`, `docs/conventions/03_CODE_STYLE.md`, `docs/conventions/README.md`,
  `RUNNING.md`, `.claude/skills/review-branch/SKILL.md`, `.claude/skills/hand-in/SKILL.md`.

## Review Focus
1. **A failing job other than the last** (e.g. backend verify red): `ci-passed` must be red, not skipped, so the PR
   stays blocked. Pinned in Task 4 (deliberately broken test on a throwaway PR commit).
2. **The pushed image is a rebuild, not the tested one**: push job must `docker load` the e2e artifact, never build.
   Pinned in Task 4 (image digest of the e2e-loaded image equals the pushed digest, compared in the log).
3. **Frontend container listens only on its container hostname** (Next standalone binds to `HOSTNAME`): with host
   networking it must bind `0.0.0.0`, or the e2e cannot reach it. Pinned in Task 2 (`curl localhost:3100/login`).
4. **Backend container starts before Keycloak is ready**: the issuer is fetched lazily on the first token (F13), so
   start order is safe; Playwright must still wait for backend health. Pinned in Task 3 (wait script polls
   `/actuator/health` and `/login` before tests).
5. **Pull requests from forks** cannot get `packages: write`: harmless because PRs never push; the workflow must not
   require secrets on PRs. Pinned in Task 4 (permissions block per job, PR run green without secrets).

---

### Task 1: The gate exposes its checks per group for CI

**Files:**
- Modify: `.githooks/gate.mjs` (header comment lines 1-15, `prePush()` at ~172-191, the CLI dispatch at ~193-198)

**Interfaces:**
- Produces: `node .githooks/gate.mjs ci <backend|frontend|repo>` runs that group's checks, exits non-zero on the first
  failure; unknown group → `fail('usage: ...')`. `pre-push` runs all three groups in the order backend, frontend, repo
  (same commands as today, still skipping docs-only pushes and still requiring Docker for backend).

- [ ] **Step 1:** Replace the inline `run(...)` calls in `prePush()` with a constant
  `CHECKS = { backend: [[label, command, cwd], ...], frontend: [...], repo: [...] }` holding exactly today's commands:
  backend = `backend verify (all backend tests, Docker)`; frontend = lint, typecheck, tests, build; repo = contract
  lint, guard hook tests. Add `runGroup(name)`. `prePush()` keeps its docs-only skip and Docker check, then runs the
  three groups. Add the `ci` branch to the dispatch; `ci` does **not** apply the docs-only skip. Update the header
  comment: "ci <group> (GitHub Actions): one group of the pre-push checks, never skipped".
- [ ] **Step 2: Verify** `node .githooks/gate.mjs ci repo` → both checks pass, exit 0;
  `node .githooks/gate.mjs ci nope` → usage error, exit 1; `node .githooks/gate.mjs pre-push < /dev/null` (Docker on)
  → same output and result as before the change.
- [ ] **Step 3: Commit** `chore(ci): the gate runs one group of its checks for CI`

### Task 2: Dockerfiles for backend and frontend

**Files:**
- Create: `backend/Dockerfile`, `backend/.dockerignore`, `frontend/Dockerfile`, `frontend/.dockerignore`
- Modify: `frontend/next.config.ts` (add `output: "standalone"`)

**Interfaces:**
- Produces: `docker build -t sharewise-backend backend` and `docker build -t sharewise-frontend frontend`.
  Backend image: listens on 8080, configured only by env (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
  `SPRING_DATASOURCE_PASSWORD`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`; defaults from
  `application.yml` apply when unset). Frontend image: `node server.js`, `HOSTNAME=0.0.0.0`, `PORT` default 3000,
  env `BACKEND_URL`, `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `APP_URL`.

- [ ] **Step 1: Backend Dockerfile.** Stage `build` from `eclipse-temurin:21-jdk`: copy `mvnw`, `.mvn`, `pom.xml`,
  run `./mvnw -q dependency:go-offline` (layer cache), copy `src`, run `./mvnw -q -DskipTests package` (tests ran in
  the backend job; the image build must not start Testcontainers). Stage runtime from `eclipse-temurin:21-jre`: copy
  the jar as `app.jar`, run as a non-root user, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`.
  `LABEL org.opencontainers.image.source="https://github.com/Ar000sh/sharewise"` (links the ghcr package to the repo).
  `.dockerignore`: `target`, `.idea`, `*.iml`.
- [ ] **Step 2: Frontend.** `next.config.ts`: add `output: "standalone"`. Dockerfile stages from `node:24-alpine`:
  `deps` (`npm ci`), `build` (`npm run build`, `NEXT_TELEMETRY_DISABLED=1`), runtime: copy `.next/standalone`,
  `.next/static` → `.next/static`, `public` → `public`; non-root user; `ENV HOSTNAME=0.0.0.0 PORT=3000`;
  `EXPOSE 3000`; `CMD ["node","server.js"]`; same `LABEL` as backend. `.dockerignore`: `node_modules`, `.next`,
  `.env*` except `.env.example`, `e2e`, `test-results`, `playwright-report`.
- [ ] **Step 3: Verify** both builds succeed. Then with `docker compose up -d db keycloak`:
  `docker run --rm -d --network host --name be sharewise-backend` → `curl -s localhost:8080/actuator/health` gives
  `{"status":"UP"...}`; `docker run --rm -d --network host -e PORT=3100 -e APP_URL=http://localhost:3100
  -e BACKEND_URL=http://localhost:8080 -e KEYCLOAK_ISSUER=http://localhost:8180/realms/sharewise
  -e KEYCLOAK_CLIENT_ID=sharewise-web --name fe sharewise-frontend` → `curl -s -o /dev/null -w "%{http_code}"
  localhost:3100/login` gives `200`. (Docker Desktop on Windows: enable Settings → Resources → Network → host
  networking first; if unavailable, verify this step in the CI run of Task 4 instead and say so.) Also
  `npm run build` and `npm run e2e` still work locally with the standalone output.
- [ ] **Step 4: Commit** twice: `build(backend): container image` and `build(frontend): container image with the
  standalone output`.

### Task 3: e2e against running images

**Files:**
- Modify: `frontend/playwright.config.ts`
- Create: `docker-compose.ci.yml`

**Interfaces:**
- Consumes: images from Task 2, referenced by env `BACKEND_IMAGE`, `FRONTEND_IMAGE`.
- Produces: `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d` starts `db`, `keycloak`,
  `backend`, `frontend` (frontend on `localhost:3100`). `E2E_BASE_URL=http://localhost:3100 npm run e2e` tests that
  URL without starting a dev server.

- [ ] **Step 1:** `playwright.config.ts`: when `process.env.E2E_BASE_URL` is set, `use.baseURL` is that URL and
  `webServer` is `undefined`; otherwise unchanged. Update the doc comment (both modes). Keep the comment on the
  expect timeout accurate (dev server compile is only the local case).
- [ ] **Step 2:** `docker-compose.ci.yml` adds services `backend` (`image: ${BACKEND_IMAGE}`, `network_mode: host`,
  `depends_on: [db]`) and `frontend` (`image: ${FRONTEND_IMAGE}`, `network_mode: host`, env `PORT=3100`,
  `APP_URL=http://localhost:3100`, `BACKEND_URL=http://localhost:8080`,
  `KEYCLOAK_ISSUER=http://localhost:8180/realms/sharewise`, `KEYCLOAK_CLIENT_ID=sharewise-web`). Header comment: why
  host networking (one `localhost` for the issuer), Linux/CI only, cloud needs its own answer.
- [ ] **Step 3: Verify** `npm run e2e` without `E2E_BASE_URL` behaves as before (local stack per `RUNNING.md`).
  If host networking is available locally: with the images of Task 2,
  `BACKEND_IMAGE=sharewise-backend FRONTEND_IMAGE=sharewise-frontend docker compose -f docker-compose.yml -f
  docker-compose.ci.yml up -d`, wait for health, `E2E_BASE_URL=http://localhost:3100 npm run e2e` → Mallorca spec
  passes. Otherwise this is proven by the CI run in Task 4.
- [ ] **Step 4: Commit** `test(frontend): e2e can run against an already running frontend` and
  `build(ci): compose override that runs the images for the e2e`.

### Task 4: The workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `gate.mjs ci <group>` (Task 1), Dockerfiles (Task 2), `docker-compose.ci.yml` + `E2E_BASE_URL` (Task 3).
- Produces: workflow `CI`, jobs `backend`, `frontend`, `repo`, `images`, `e2e`, `publish`, `ci-passed`.

- [ ] **Step 1: Write `ci.yml`.** Triggers: `pull_request` (all branches), `push` to `main`. `concurrency:
  group: ci-${{ github.ref }}`, `cancel-in-progress: true`. Top-level `permissions: contents: read`.
  - `backend`: ubuntu-latest, `actions/setup-java` (temurin 21, `cache: maven`), `actions/setup-node` (24, for the
    gate), `node .githooks/gate.mjs ci backend`; on failure upload `backend/target/surefire-reports` and
    `failsafe-reports`.
  - `frontend`: setup-node 24 with npm cache on `frontend/package-lock.json`, `npm ci` in `frontend`,
    `node .githooks/gate.mjs ci frontend`.
  - `repo`: setup-node 24, `node .githooks/gate.mjs ci repo`.
  - `images` (needs the three): `docker/setup-buildx-action`, two `docker/build-push-action` with `load: true`,
    `push: false`, tags `sharewise-backend:${{ github.sha }}` / `sharewise-frontend:${{ github.sha }}`, GitHub
    Actions layer cache (`cache-from/to: type=gha,scope=<app>`); `docker save` both into `images.tar`; print both
    image IDs; upload artifact `images` (retention 1 day).
  - `e2e` (needs `images`): download + `docker load`; `docker compose -f docker-compose.yml -f docker-compose.ci.yml
    up -d` with `BACKEND_IMAGE`/`FRONTEND_IMAGE` set to the SHA tags; wait (max 180 s) until
    `localhost:8180/realms/sharewise`, `localhost:8080/actuator/health` and `localhost:3100/login` answer 200;
    setup-node, `npm ci`, `npx playwright install --with-deps chromium`, `E2E_BASE_URL=http://localhost:3100 npm run
    e2e`. On failure: upload `frontend/test-results` and `docker compose logs` output.
  - `publish` (needs `e2e`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`,
    `permissions: packages: write, contents: read`): download + `docker load`, `docker/login-action` to ghcr.io with
    `GITHUB_TOKEN`, `docker tag` each loaded image to `ghcr.io/ar000sh/sharewise-<app>:${{ github.sha }}` and
    `:latest`, `docker push` all four; print the image IDs again (Review Focus 2).
  - `ci-passed` (needs all six, `if: always()`): fails when any needed job's result is `failure` or `cancelled`
    (`publish` being `skipped` on PRs is fine). This is the required check.
- [ ] **Step 2: Commit** `ci: pipeline with checks, tests, images, e2e and ghcr push`.
- [ ] **Step 3: Verify** push the branch, open the PR with `gh pr create` (title `CI/CD pipeline`, body from the
  design's Intent). All jobs green, `publish` skipped, `ci-passed` green.
- [ ] **Step 4: Verify the red path** (Review Focus 1): push a commit that breaks one backend test assertion →
  `backend` red, `images`/`e2e` skipped, `ci-passed` **red**. Revert it with `git revert` → green again. Report both
  run URLs. (The break and revert stay in history; a force-push to drop them only with the user's yes.)

### Task 5: Docs and skills follow the PR flow

**Files:**
- Modify: `CLAUDE.md` (line 36 "Branches ...", workflow table row "Before merging a branch"),
  `docs/conventions/03_CODE_STYLE.md:57` (Branches rule), `docs/conventions/README.md` (enforcement table: new row
  "CI pipeline | `.github/workflows/ci.yml` | any group of the push gate, the image build or the e2e against the
  images fails; required for merging into `main`"; the "Everything must be green" paragraph mentions CI),
  `RUNNING.md` (Prerequisites: `gh` installed and `gh auth login`; new section "Images and CI": build/run the images,
  the compose override, `E2E_BASE_URL`, where the images live, Docker Desktop host-networking note),
  `.claude/skills/review-branch/SKILL.md` step 6, `.claude/skills/hand-in/SKILL.md` steps 1/3.

- [ ] **Step 1:** Branch rule everywhere: work arrives on `main` only by pull request with green `ci-passed`; merge
  method "Create a merge commit" with subject `merge: <branch> (<one line>)`; Claude opens PRs with `gh pr create`;
  the user (GitHub UI) or Claude (`gh pr merge --merge --subject "merge: ..."`) merges. review-branch step 6: push the
  branch, open/update the PR, merge only on the user's word and only when `ci-passed` is green; then
  `git switch main && git pull`. hand-in: step 1 adds "after `git pull`", step 3 adds "the latest `CI` run on `main`
  is green". Grep for old terms: `grep -rn "no-ff\|merge --no-ff" CLAUDE.md docs .claude` → no stale hit outside
  `docs/archive` and dated design docs.
- [ ] **Step 2: Verify** `git diff --stat` shows no whole-file line-ending flips.
- [ ] **Step 3: Commit** `docs(shared): changes reach main by pull request with green CI` (skills in the same commit:
  one concern, the PR flow).

### Task 6: GitHub settings (the user, in the browser) and first publish

Not code; a checklist for the user, done after Task 4 ran once (the check name must exist before it can be required).

- [ ] **Step 1:** Repo Settings → General → Pull Requests: allow merge commits only (untick squash and rebase).
- [ ] **Step 2:** Settings → Actions → General → Workflow permissions: "Read repository contents" is enough (the
  `publish` job asks for `packages: write` itself).
- [ ] **Step 3:** Settings → Rules → Rulesets → new branch ruleset for `main`, enforcement Active, **no bypass list**:
  restrict deletions, block force pushes, require a pull request (0 approvals), require status check `ci-passed`
  (not "up to date"), restrict updates without PR.
- [ ] **Step 4:** Merge this PR (merge commit, subject `merge: feature/cicd-pipeline (CI/CD pipeline)`). The run on
  `main` publishes both images. On github.com → Packages, set both packages to **public** if they are not, and check
  they are linked to the repo.
- [ ] **Step 5: Verify** `docker pull ghcr.io/ar000sh/sharewise-backend:<merge sha>` works without login;
  `git push origin main` of a local dummy commit is rejected (then drop the local commit).
