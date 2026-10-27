# ShareWise conventions

These rules exist because the parallel build produced four slightly different codebases: different folder layouts, different names for the same concept, different test styles. **There is one way to do each thing below. If you think a rule is wrong, change the rule in the same commit as the code, never only the code.**

| File | Covers |
|---|---|
| [01_ARCHITECTURE_AND_NAMING.md](01_ARCHITECTURE_AND_NAMING.md) | Module layout, layers, dependency rules, the vocabulary from the domain diagram, naming of every kind of class |
| [02_TESTING.md](02_TESTING.md) | Test levels, naming, fixtures, what to test, how to run |
| [03_CODE_STYLE.md](03_CODE_STYLE.md) | Formatting, comments and Javadoc, errors, API and database conventions, commits and branches |
| [04_FRONTEND.md](04_FRONTEND.md) | Next.js and TypeScript rules, file naming, API access, money, errors, tests |

## Source of truth, in this order
1. **The domain diagram** (`work/ShareWise_Domain_Model.drawio` in the study repo, the one shown in presentation 1) decides the *names* of aggregates, entities, value objects, enumerations, events, operations and fields. Code follows it. Where the code needs something the diagram lacks, the diagram is updated in the same change (see the list "diagram deltas" in `docs/2026-10-27-diagram-alignment-design.md`).
2. **`docs/api/openapi.yaml`** decides the HTTP interface. It is changed first, in its own commit.
3. **These conventions** decide everything else.

## How the rules are enforced (a rule nobody checks is a suggestion)
| Check | Where | Fails the build when |
|---|---|---|
| `ModularityTests` (Spring Modulith) | `backend/src/test/.../ModularityTests` | a module uses another module's internals or an undeclared module |
| `ConventionTests` (ArchUnit) | `backend/src/test/.../ConventionTests` | layering, package placement or class-suffix rules of document 01 are broken |
| Java formatter check | `./mvnw validate` (`spring-javaformat:validate`) | a file is not formatted; fix with `./mvnw spring-javaformat:apply` |
| ESLint + `tsc` | `npm run lint`, `npm run typecheck` | frontend rules of document 04 are broken |
| Contract lint | `npx @redocly/cli lint docs/api/openapi.yaml` | the contract is invalid |

Everything must be green before a commit lands on `main`:
```
cd backend  && ./mvnw verify
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
npx @redocly/cli lint docs/api/openapi.yaml
```
(`npm run e2e` before a release or demo; it needs the backend and Postgres running.)

## Definition of done for any change
- [ ] Names come from the diagram and the glossary, not invented.
- [ ] Contract changed first (if the HTTP interface changed), types regenerated (`npm run gen:api`).
- [ ] New behaviour has a test at the right level (document 02); a rejected call is tested to leave nothing behind.
- [ ] Public types and operations have the Javadoc described in document 03.
- [ ] All checks above are green.
- [ ] `RUNNING.md` and the diagram are updated if setup or the model changed.
- [ ] One concern per commit, message in the format of document 03.
