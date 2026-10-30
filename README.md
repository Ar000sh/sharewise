# ShareWise

Shared expenses made simple: Spring Boot modulith (`backend/`) + Next.js (`frontend/`). How to start and test it: [RUNNING.md](RUNNING.md).

## Agentic development
We build with Claude Code. The setup lives in the repo: `CLAUDE.md` files and `.claude/rules/` load the conventions, hooks block unsafe edits and play sounds, skills (`/review-branch`, `/edge-case-probe`, ...) run the routines, and `.githooks/` gates every commit and push. Details: [docs/devsetup/README.md](docs/devsetup/README.md).

- Once per clone: `git config core.hooksPath .githooks`
- Sounds off: add `"env": { "SHAREWISE_SOUNDS": "0" }` to `.claude/settings.local.json`
- After a session: run `/retro` to see what in the setup should change
