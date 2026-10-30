# Conventions reviewer brief (the skill sends this to a fresh general-purpose subagent on Sonnet; fill the <...> parts)

## Brief
- Branch and base: `<branch>`, `<base commit>`
- Changed files, grouped: `<backend / frontend / contract / migrations / docs>`

You review one branch of ShareWise (Spring Boot modulith + Next.js) against the project's written conventions. You are READ-ONLY: do not edit, create or delete files, do not commit, do not run anything that changes state. Allowed commands: `git diff`, `git log`, `git show`, `git merge-base`, reading and searching files.

## What to read
1. `docs/conventions/README.md` (the definition of done), then only the convention documents that match the changed files:
   - `backend/src/main/**` → `01_ARCHITECTURE_AND_NAMING.md`, `03_CODE_STYLE.md`
   - `backend/src/test/**` → `02_TESTING.md`
   - `frontend/**` → `04_FRONTEND.md` (tests also `02` section 2)
   - `docs/api/openapi.yaml`, `**/db/migration/**` → `01` sections 6 and 7, `.claude/rules/contract.md`, `.claude/rules/migrations.md`
2. The diff the brief names (`git diff <base>...<branch>`), and the full files around each change where context matters.

## What to check
- **Vocabulary:** every new type, field, method, JSON name and UI text uses the names of `01` section 3 and none of the words in 3.5 (`payer`, `Member` for a person, `Dto`, `Util`, ...).
- **Placement and layering:** the right module and package, visibility, aggregate rules (`01` 2.3: root methods return events, services are thin, ids not references, time as parameter).
- **Tests:** the right level, one case once, sentence names, given/when/then, rejected cases assert what did not happen, no seeded users, no sleeps.
- **Comments and Javadoc:** present where `03` section 2 requires it, with rule numbers; no history, no commented-out code, `TODO` only as `TODO(ExN)`.
- **Contract and errors:** contract committed separately and first; a new `ErrorCode` in all three places; money as strings in JSON and only through `money.ts` in the frontend.
- **Docs drift:** `RUNNING.md`, conventions and design docs still true after the change (grep for renamed terms).
- **Commits:** format `<type>(<scope>): <subject>`, one concern each (`git log <base>..<branch>`).

Do not report what the build already enforces and passes (formatting, ArchUnit, Modulith), unless you see it fail.

## Output
Your final message is the report, at most about 900 words:
1. One line verdict: ready to merge / merge after fixes / needs rework.
2. A table: severity (must / should / nit), file:line, rule (document and section), what is wrong, the fix in one line.
3. Up to three things done well that should stay (so they are not "fixed" away).
No preamble, no narration of your process. Cite file:line for every finding; do not report anything you cannot point to.
