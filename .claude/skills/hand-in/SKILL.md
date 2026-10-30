---
name: hand-in
description: Prepare an exercise hand-in (ex4, ex5, ...) of ShareWise - run every gate and the e2e tests, check docs against the code, tag the submitted commit, update the FAVS study status. Manual only; pass the exercise as argument, e.g. /hand-in ex5.
disable-model-invocation: true
---

# Hand in an exercise

Argument: the tag, e.g. `ex5`. A tag marks what was submitted and is never moved afterwards, so every step before the tag must be green.

## Steps
1. **State:** on `main`, clean working tree, up to date with `origin/main`. No open feature branch that belongs to this exercise (ask if there is one).
2. **Scope check:** read the exercise's design doc in `docs/` (and the exercise text if the user has it) and list each required item with where it is implemented and tested. Show gaps before going on.
3. **All gates:** `node .githooks/gate.mjs pre-push < /dev/null` (Docker must run). Then the e2e tests: database and backend running (`RUNNING.md`), `cd frontend && npm run e2e`.
4. **Docs against code:** `RUNNING.md` (commands, test counts, demo steps actually work), the conventions (any rule the exercise changed), `TODO` search (`grep -rn "TODO" backend/src frontend/src`: only `TODO(ExN)` for later exercises may remain). Fix drift in a `docs:` commit.
5. **Edge cases:** if `/edge-case-probe` attack mode has not run on the new modules of this exercise, say so and ask whether to run it first.
6. **Tag** after the user's yes: `git tag -a <exN> -m "<exN>: <one line>"`; push the tag only when the user says so (`git push origin <exN>`).
7. **Study status (only if the FAVS folder exists next to this repo):** update the exercise row in `../CLAUDE.md` ("Exercise status") and the hub section of `../work/sharewise_SYNC.md` if it is still in use.
8. Suggest `/retro` for the exercise.
