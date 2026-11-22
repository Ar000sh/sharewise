# ShareWise

Shared expenses made simple: Spring Boot modulith (`backend/`) + Next.js (`frontend/`). How to start and test it: [RUNNING.md](RUNNING.md).

## Progress and review notes

- **[Team deployment guide: concepts, roles, GitHub pipeline, VM commands and troubleshooting](docs/TEAM_DEPLOYMENT_GUIDE.md)** — updated 20 November 2026: private automatic deployment workflow implemented and verified on the VM, with scheduled publication checks, database backup and health checks.
- [Deployment configuration reference](DEPLOYING.md)

- [Progress, decisions, verification, saved screenshots and requirements review (08.11.2026)](docs/2026-11-08-progress-and-handoff.md)
- [Frontend experience and sign-in design](docs/2026-11-08-frontend-experience-design.md)
- [CI/CD special cases and pending coverage](docs/2026-11-08-ci-cd-special-cases.md)
- [Create-group runtime failure: diagnosis and verification](docs/2026-11-08-create-group-runtime-fix.md)

These are dated snapshots. The confirmed first priorities for CI/CD are environment variables/secrets, database migrations and realistic interruption/crash cases. The cases distinguish inspected source behavior from pipeline coverage; they are not evidence that a pipeline or the proposed fixes have been implemented.

## Agentic development
We build with Claude Code. The setup lives in the repo: `CLAUDE.md` files and `.claude/rules/` load the conventions, hooks block unsafe edits and play sounds, skills (`/review-branch`, `/edge-case-probe`, ...) run the routines, and `.githooks/` gates every commit and push. Details: [docs/devsetup/README.md](docs/devsetup/README.md).

- Once per clone: `git config core.hooksPath .githooks`
- Sounds off: add `"env": { "SHAREWISE_SOUNDS": "0" }` to `.claude/settings.local.json`
- After a session: run `/retro` to see what in the setup should change
