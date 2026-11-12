# ShareWise conventions

These rules exist because the parallel build produced four slightly different codebases: different folder layouts, different names for the same concept, different test styles. **There is one way to do each thing below. If you think a rule is wrong, change the rule in the same commit as the code, never only the code.**

| File | Covers |
|---|---|
| [01_ARCHITECTURE_AND_NAMING.md](01_ARCHITECTURE_AND_NAMING.md) | Module layout, layers, dependency rules, the vocabulary from the domain diagram, naming of every kind of class |
| [02_TESTING.md](02_TESTING.md) | Test levels, naming, fixtures, what to test, how to run |
| [03_CODE_STYLE.md](03_CODE_STYLE.md) | Formatting, comments and Javadoc, errors, API and database conventions, commits and branches |
| [04_FRONTEND.md](04_FRONTEND.md) | Next.js and TypeScript rules, file naming, API access, money, errors, tests |

## Source of truth, in this order
1. **The domain diagram** (`docs/domain/ShareWise_Domain_Model.drawio`: team version 17.10.26 plus the Account context with an external identity provider) decides the *names* of aggregates, entities, value objects, enumerations, events, operations and fields. Code follows it. Where the code needs something the diagram lacks, the diagram is updated in the same change (see the list "diagram deltas" in `docs/2026-10-27-diagram-alignment-design.md`).
2. **`docs/api/openapi.yaml`** decides the HTTP interface. It is changed first, in its own commit.
3. **These conventions** decide everything else.

## How the rules are enforced (a rule nobody checks is a suggestion)
| Check | Where | Fails the build when |
|---|---|---|
| `ModularityTests` (Spring Modulith) | `backend/src/test/.../ModularityTests` | a module uses another module's internals or an undeclared module |
| `ConventionTests` (ArchUnit) | `backend/src/test/.../ConventionTests` | layering, package placement or class-suffix rules of document 01 are broken |
| Java formatter check | `./mvnw validate` (`spring-javaformat:validate`) | a file is not formatted; fix with `./mvnw spring-javaformat:apply` |
| ESLint + `tsc` | `npm run lint`, `npm run typecheck` | frontend rules of document 04 are broken |
| Theme rules (dark mode) | `frontend/src/lib/themeRules.test.ts` (in `npm test`) | a component or page uses a fixed black, white or grey, a fixed background without its own text colour, or a coloured text without its `dark:` counterpart (document 04, section 5) |
| Contract lint | `npx @redocly/cli lint docs/api/openapi.yaml` | the contract is invalid |
| Commit gate | `.githooks/pre-commit` | a secret or key is staged, the contract is mixed with code, the lock file lost platforms, an existing file flips its line endings (CRLF <-> LF), or the staged part fails format/lint/typecheck |
| Push gate | `.githooks/pre-push` | any check above fails. Backend `verify` (Docker required), the frontend checks and the contract lint run at the same time; a group the push cannot affect is skipped (a frontend-only push skips the backend tests). CI runs all of them anyway |
| CI pipeline | `.github/workflows/ci.yml` (runs `.githooks/gate.mjs ci <group>`) | any group of the push gate, the image build or the e2e test against the built images fails; required for every pull request into `main`, and `main` builds push the images to ghcr.io |
| Guard hook (Claude only) | `.claude/hooks/guard.mjs` | Claude edits a secrets file, `schema.d.ts`, a migration on `main`, or uses `--no-verify` |

Everything must be green before a commit lands on `main`:
```
cd backend  && ./mvnw verify
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
npx @redocly/cli lint docs/api/openapi.yaml
```
(`npm run e2e` before a release or demo; it needs the backend and Postgres running. The CI pipeline runs it on every pull request, against the built images, so nobody has to remember.)

## Definition of done for any change
- [ ] Names come from the diagram and the glossary (`docs/domain/Ubiquitous_Language.md`), not invented.
- [ ] Contract changed first (if the HTTP interface changed), types regenerated (`npm run gen:api`).
- [ ] New behaviour has a test at the right level (document 02); a rejected call is tested to leave nothing behind.
- [ ] Public types and operations have the Javadoc described in document 03.
- [ ] No duplicated logic and nothing built for a case that cannot happen (document 03, section 7).
- [ ] All checks above are green.
- [ ] `RUNNING.md` and the diagram are updated if setup or the model changed.
- [ ] One concern per commit, message in the format of document 03.
