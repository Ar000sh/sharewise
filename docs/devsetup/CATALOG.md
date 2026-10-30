# Dev setup catalog

Everything we add to the setup, why, and whether it earned its place. Add a row when something is added; fill in the evidence column when it helps (or note that it did not). `/retro` proposes removing rows that never helped. How the setup is organised: [README.md](README.md).

| Added | Item | Kind | Why (what hurt) | Evidence it helped |
|---|---|---|---|---|
| 2026-10-27 | `docs/conventions/01`–`04` + `ConventionTests` (ArchUnit) + `ModularityTests` | conventions + build checks | The Ex4 parallel build produced four slightly different codebases: layouts, names and test styles diverged | The merge into `main` needed a whole alignment refactor; since then new code follows one layout and the build rejects boundary breaks |
| 2026-10-30 | Root, `backend/`, `frontend/` `CLAUDE.md` with imports of the conventions | memory | Coding sessions loaded about 54 KB of study notes from the parent folder, but not the 37 KB of coding rules; Claude had to remember to read them | New |
| 2026-10-30 | `frontend/CLAUDE.md` imports `AGENTS.md` | memory | Next.js 16 writes its "this is not the Next.js you know" warning into `AGENTS.md`, which Claude Code never reads | New |
| 2026-10-30 | `.claude/rules/` (backend-domain, backend-tests, migrations, contract, frontend-tests) | path-scoped rules | Rules for one kind of file were only in long documents; they now load exactly when such a file is touched | New |
| 2026-10-30 | `.claude/hooks/guard.mjs` + 8 tests (PreToolUse) | hook | Conventions said "never edit `schema.d.ts`", "never edit a migration on main", "never commit secrets" but nothing stopped it. Ported from the `workflows` repo's `guard.py`, where it blocked a key from reaching `.env.example` | New |
| 2026-10-30 | `.githooks/` gates (`gate.mjs`): fast pre-commit, full pre-push | git hooks | "`main` stays green" and "contract in its own commit" were trust-only. In `workflows` the same kind of gate has run on every commit since 2026-10-22 | New. Watch: pre-push time (backend verify needs Docker) |
| 2026-10-30 | Lock-file check in the pre-commit gate | git hook | npm on Windows drops other platforms' optional packages from `package-lock.json` (convention 04 section 7 existed, unchecked) | New |
| 2026-10-30 | Sound hooks moved into the repo, opt-in per person | hook | They lived in the FAVS folder and did not play in sessions started in `sharewise/` | New |
| 2026-10-30 | Skills `edge-case-probe`, `design-review`, `retro` (adapted from `workflows`) + agents `edge-case-attacker`, `design-reviewer` | skills + agents | In `workflows` a fresh attacker found 13 reproduced issues our own tests missed (5 fixed), a design review found model flaws behind about 40 % of the special cases; ShareWise has the same risk in money, lifecycle and events | Not used here yet |
| 2026-10-30 | Skill `review-branch` + agent `conventions-reviewer` (Sonnet, read-only) | skill + agent | Merges were reviewed by hand; conventions drift (names like `payer`, missing Javadoc rule numbers) is not caught by the build | Not used yet |
| 2026-10-30 | Skills `contract-change`, `hand-in`, `backend:domain-operation` | skills | The most error-prone multi-step routines: a new `ErrorCode` belongs in three places, the contract goes first, a hand-in tag cannot be moved | Not used yet |
| 2026-10-30 | Permission allowlist in `.claude/settings.json` | config | Routine read-only and build commands asked for permission each time | New |

## What subagents inherit
Not yet checked in this repo. In `workflows` (2026-10-23) a subagent got the project `CLAUDE.md`, saw the model-invocable skills (not the manual-only ones) and was blocked by the guard hook. Check once here, with a cheap agent, and record the result.

## Candidates (decide when the need shows up)
- A `frontend:new-page` skill (route + proxy handler + `useApi` + tests), if building pages turns out repetitive in Ex5.
- A diagram-sync check: compare type and field names in `backend/src/main` with the names in the draw.io diagram.
- CI pipeline (Ex7): run `gate.mjs pre-push` in the pipeline so the same checks guard the remote.
