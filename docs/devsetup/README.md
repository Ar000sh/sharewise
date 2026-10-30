# Dev setup: how Claude Code and the gates are organised

Everything lives in this repo, so every clone and every teammate gets the same setup. The rules themselves stay in `docs/conventions/`; the setup only loads, checks and applies them.

## One-time setup per clone
```sh
git config core.hooksPath .githooks     # turns on the commit and push gates (Node 22+ needed)
```
Optional, personal, not committed: `.claude/settings.local.json`, e.g.
```json
{
  "env": { "SHAREWISE_SOUNDS": "1" },
  "claudeMdExcludes": ["**/<folder above this repo>/CLAUDE.md"]
}
```
`SHAREWISE_SOUNDS` turns on the done/attention sounds (Windows). `claudeMdExcludes` keeps a `CLAUDE.md` in a parent folder (for example a study folder) out of coding sessions. Start Claude Code in the repo root.

## Layers (what loads when)
| Layer | File(s) | Loads / runs | Holds |
|---|---|---|---|
| Repo-wide | `CLAUDE.md` (+ `docs/conventions/README.md`) | always | sources of truth, how we work, workflow by stage, definition of done |
| Backend | `backend/CLAUDE.md` (+ conventions 01, 03) | when Claude works in `backend/` | module layout, commands, pitfalls |
| Frontend | `frontend/CLAUDE.md` (+ `AGENTS.md`, conventions 04) | when Claude works in `frontend/` | Next.js 16 warning, commands, money/errors/API rules |
| File-type rules | `.claude/rules/*.md` (`paths:` frontmatter) | when Claude touches matching files | domain packages, backend tests (+ conventions 02), migrations, the contract, frontend tests |
| Guard hook | `.claude/hooks/guard.mjs` | before every Write/Edit/Bash by Claude | blocks secrets files, `schema.d.ts`, migrations already on `main`, API keys, `--no-verify` |
| Sound hook | `.claude/hooks/sound.mjs` + `.claude/sounds/` | when Claude stops or needs attention | opt-in via `SHAREWISE_SOUNDS` |
| Git gates | `.githooks/pre-commit`, `pre-push` → `gate.mjs` | every commit / push, people and Claude | see the header of `gate.mjs` |
| Skills (repo) | `.claude/skills/*` | on demand (`/name`) | `edge-case-probe`, `design-review`, `review-branch`, `contract-change`, `retro`*, `hand-in`* |
| Skills (backend) | `backend/.claude/skills/*` | on demand (`/backend:name`) | `domain-operation` |
| Agents | `.claude/agents/*` | dispatched by the skills | `conventions-reviewer` (Sonnet, read-only), `edge-case-attacker` (Opus, writes only gitignored probes), `design-reviewer` (Opus, read-only) |
| Permissions | `.claude/settings.json` | always | routine commands allowed without a prompt; force push denied |

\* manual only (`disable-model-invocation`): Claude never starts them on its own.

## Where a new rule goes
Use the table in `.claude/skills/retro/SKILL.md` (step 2). In short: a rule for the code goes into `docs/conventions/` (plus an ArchUnit test if it can be checked); a must-never with no judgment becomes a gate or guard check with a test; a procedure becomes a skill. Every addition gets a row in [CATALOG.md](CATALOG.md); retros are logged in [retro-log.md](retro-log.md).

## Testing the setup
```sh
node --test .claude/hooks/guard.test.mjs   # the guard hook (also run by the gates when hooks change)
```
