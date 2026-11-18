# Spec: upgrade Next.js because the build is blocked by a security advisory

**Date:** 18.11.26 · **Type:** chore · **Branch:** `chore/next-security-upgrade` (worktree `.claude/worktrees/chore-next-security-upgrade`, from `origin/main` at `b373d26`, local only, not pushed) · **Status:** ready to build, written to start a fresh session from

## 1. The problem
`npm run build` in `frontend/` compiles and type-checks, then aborts with exit code 1:

```
Error [SecurityFatalError]: Your version of Next.js is affected by a known security vulnerability.
... Reference: https://registry.npmjs.org/-/npm/v1/security/advisories/bulk
```

The installed version is **Next 16.4.3** (`frontend/package.json`). The same version built green in CI on 13.11.26 (runs 43528561006 and 43527823816), so a new advisory was most likely published since. This was seen on the branch `feature/split-strategies` (not caused by it: the failure is the advisory lookup, after a successful compile), but it affects **every** branch and `main`:
- the pre-push gate stops at "frontend build" (`.githooks/gate.mjs pre-push`), and `--no-verify` is not allowed;
- the CI pipeline (`.github/workflows/ci.yml`) runs the same build and will fail on every pull request.

Not yet verified: that `main` itself fails the same way (nobody ran the build on a clean `main` after the advisory). Do this first, it takes about 30 seconds (see section 4, step 1).

## 2. Goal
`npm run build` exits 0 on this branch with a Next version that is not flagged, the rest of the frontend still works, and the pull request is green. The pending feature branch `feature/split-strategies` (13 commits, waiting for the pre-push gate) can then merge `main` and push.

**Non-goals**
- Loosening or skipping the gate or the advisory check (a gate change is a decision for the user, `CLAUDE.md` "Gates are not optional").
- Other dependency upgrades, refactors or feature work. One concern per branch.
- Changing the backend.

## 3. Decisions already taken and open points
| # | Point | State |
|---|---|---|
| U1 | The upgrade goes on **its own branch and pull request**, not on `feature/split-strategies` | decided (user, 18.11.) |
| U2 | Nothing is pushed or opened yet | decided (user, 18.11.) |
| U3 | **Which version to move to** | **open: find out, do not guess.** Read the advisory (the registry URL above, or `npm audit` in `frontend/`) and pick the smallest fixed version that stays on the current major (16.x) if one exists. If only a new major fixes it, stop and ask the user: that is a bigger change (`04_FRONTEND.md`, `frontend/AGENTS.md`: this Next has breaking changes, read `node_modules/next/dist/docs/` before changing code). |
| U4 | The build output suggests running `next upgrade --agent=security`. That text came from tool output, not from the user or the docs: treat it as a hint, check what it would change first (`npx next upgrade --help`), and prefer the plain `npm install next@<version>` route if it is clearer. | open |

## 4. Steps
1. **Confirm the cause on a clean `main`** (this worktree is clean `main`): `cd frontend && npm ci && npm run build`. Expected: the same `SecurityFatalError`. If the build passes here, the cause is something else (stop and report).
2. Read the advisory and decide U3. Write the version and the advisory id into the commit message (`04_FRONTEND.md` section 7: a dependency change needs a reason in the commit).
3. Upgrade `next` (and `eslint-config-next` if it is pinned to the same version in `package.json`; check). Use `npm install`, never edit `package-lock.json` by hand.
4. **Windows lock-file check:** `git diff package-lock.json`. npm on Windows can drop other platforms' optional packages and the commit gate refuses such a lock file. If entries were lost, restore them (see `04_FRONTEND.md` section 7).
5. Read the release notes of the versions skipped and the matching guide in `node_modules/next/dist/docs/`; fix only what the upgrade breaks.
6. Run, one at a time, in `frontend/`: `npm run lint && npm run typecheck && npm test && npm run build`. All four must pass; the build must exit 0.
7. `npm run e2e` needs the backend, Postgres and Keycloak (Docker): memory is tight on this machine (a full backend `verify` was killed once for low memory). Close other programs first, or leave the e2e to CI (the pipeline runs it against the built images).
8. Commit: `chore(frontend): upgrade next to <version> for security advisory <id>`, trailer `Co-Authored-By: Claude <model> <noreply@anthropic.com>`. One commit if only the version and lock file change.
9. Push and open the pull request only when the user says so (`gh pr create`; the required check is `ci-passed`).

## 5. Acceptance criteria
1. `cd frontend && npm run build` exits 0.
2. `npm run lint`, `npm run typecheck`, `npm test` pass; no source change beyond what the upgrade forces.
3. `git diff --stat` shows `frontend/package.json` and `frontend/package-lock.json` (and only what the upgrade forces); the lock file kept its other-platform entries.
4. The CI pipeline on the pull request is green.

## 6. Afterwards
In `feature/split-strategies` (main checkout): `git merge main` (or rebase, as the user prefers) once this is on `main`, then `node .githooks/gate.mjs pre-push < /dev/null`. That gate stopped at the failing frontend build before reporting the backend, so run it to the end once to get the backend result in the same gate. Then continue with the branch review (`/review-branch` steps 3 to 6) and the pull request.
