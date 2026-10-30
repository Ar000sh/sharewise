# ShareWise (code repository)

"Shared expenses made simple": a group expense app (like Splitwise) built for the FAVS module (Hochschule Hannover, WS 26/27). Spring Boot modulith in `backend/`, Next.js in `frontend/`. The path of the semester: modular monolith → containers → CI/CD → cloud → microservices with messaging. Study notes live in the separate FAVS folder; this repo holds code and its docs only.

`backend/CLAUDE.md` and `frontend/CLAUDE.md` hold the rules for each side; `.claude/rules/` adds rules for specific file types (domain, tests, migrations, contract). How the whole setup is organised: `docs/devsetup/README.md`.

## Sources of truth, in this order
1. **The domain diagram** (`work/ShareWise_Domain_Model.drawio` in the FAVS study folder) decides the names of aggregates, entities, value objects, events and operations. A code name the diagram lacks is a defect in one of the two.
2. **`docs/api/openapi.yaml`** decides the HTTP interface. It changes first, in its own commit.
3. **`docs/conventions/`** decides everything else. If a rule is wrong, change the rule in the same commit as the code.
4. Business rules are numbered R1 to R17 and L1 to L6 (`work/ShareWise_Requirements.md` in the FAVS folder). Code and Javadoc cite them: `(R4)`.

@docs/conventions/README.md

## How we work
- **Explain the why.** This is a study project that gets presented and defended (P2 17.11, P3 15.12). For every design choice say briefly why and which concept it practises (aggregate rule, eventual consistency, outbox, ...). When a review or a plan has more than a handful of points, start with a short overview of what was built and how the parts fit, then the details.
- **Who decides what.** A choice that changes what a user sees, the shape of shared state (statuses, schemas, events, module dependencies, transaction boundaries) or that is hard to reverse goes to the user when it comes up, with the options and the cost if wrong. Cosmetic or easily undone choices may be taken without asking, but list them at the end as "(call)" so they can be overruled.
- **Never present an inference as a decision of the professor.** Say what comes from the slides and what is our choice.
- **Docs change with the code.** When behaviour changes, update `RUNNING.md`, the conventions, the design doc and the comments in the same commit, and grep for the old term.
- **Gates are not optional.** `.githooks/` checks every commit and push (enable once per clone: `git config core.hooksPath .githooks`). Never use `--no-verify`; if a gate is wrong or too slow, ask whether to change it.
- **Never write regexes or backslashes through shell heredocs or `node -e`.** Escapes get mangled. Use Write/Edit and prove each regex with a test.
- **Verify money with a script** before writing balances or examples into docs (the Mallorca numbers are checked that way).

## Workflow by stage
| Stage | Do |
|---|---|
| New feature (an exercise, a journey) | superpowers brainstorming → design doc `docs/YYYY-MM-DD-<topic>-design.md` → `/edge-case-probe` plan mode; `/design-review` when it touches statuses, money or several writers → plan |
| HTTP interface changes | `/contract-change` |
| New aggregate operation | `/backend:domain-operation` |
| Before merging a branch | `/review-branch` (conventions + diagram names, fresh reviewer) |
| A module or exercise is done | `/edge-case-probe` attack mode, then `/hand-in exN` |
| End of an exercise, or something went badly | `/retro` (manual) |

Branches: `feature/`, `fix/`, `refactor/`, `chore/`, merged with `--no-ff`. Commits: `<type>(<scope>): <subject>`, one concern each, trailer `Co-Authored-By: Claude <model> <noreply@anthropic.com>` naming the model that did the work.

Commands: `RUNNING.md`. Docker Desktop must run for backend tests.
