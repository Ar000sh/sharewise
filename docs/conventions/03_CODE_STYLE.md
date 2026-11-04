# 03 Code style, comments, documentation, git

## 1. Formatting (Java)

- Format is **Spring Java Format** (tabs, code up to 120 columns, Javadoc and comments wrapped at 90 by the formatter, `}` and `else`/`catch` on separate lines). It is enforced: `./mvnw spring-javaformat:apply` formats, `./mvnw validate` fails on unformatted files. Do not argue with the formatter and do not hand-format.
- Imports: no wildcards, no unused imports, static imports only in tests. The formatter orders them.
- Constructor injection only, fields `private final`, no `@Autowired` on fields in production code (tests may use it).
- `record` for events, commands, requests, responses, read models and value objects. No Lombok (it is not used; the dependency is removed).
- `Optional` only as a return type, never as a field or parameter. Collections returned from the domain are unmodifiable copies (`List.copyOf`). No `null` returns from public methods.
- One top-level type per file; a nested type only when it is meaningless on its own (a service's private view record).
- `var` is fine when the right-hand side names the type; not for literals or method results whose type matters.

## 2. Comments and Javadoc

The code says *how*; comments say *why* and *which rule*. Language: English, full sentences, no jokes, no names of people.

| Where | Required | Content |
|---|---|---|
| Every public type in `api` and every aggregate/entity/value object | Javadoc, 1 to 3 sentences | the business meaning in domain words; the invariant it protects; the diagram name if the Java name differs |
| Every aggregate operation (the methods named after the diagram) | Javadoc | the rule in one sentence, the rule numbers it enforces `(R4, R6)`, the event it returns, the `DomainException` codes it throws (`@throws`) |
| Every `api` port method | Javadoc | the question it answers, whether it works without a transaction, what it returns for an unknown group/user |
| Services, controllers, repositories | one-line class Javadoc | its role; no Javadoc on methods that only follow the naming rules |
| Inside methods | only to explain a non-obvious *why* | e.g. `// remainder cents go to the first participants so the shares add up exactly (R4)` |

- Reference the sources of a rule instead of restating them: requirement numbers `(R4)`, decision numbers `(D10)`, diagram decisions `(diagram 17.10)`.
- No commented-out code, no banner comments, no `// getter`, no comment that repeats the name of what it documents.
- `TODO` is allowed only as `TODO(ExN): what and why` (`N` the exercise that does it), and only for work that is already planned. Search for `TODO` before every release.
- Test comments: only the `// given // when // then` markers and the occasional `// why` for a surprising setup.

## 3. Errors

- Business rule violations throw `DomainException(ErrorCode, message)` from the domain or the service. The message is a sentence a user could read; the `ErrorCode` is the stable machine contract.
- Never catch `Exception` to hide it; never swallow. Programming errors stay `IllegalStateException`/`IllegalArgumentException` and become a 500.
- Every new `ErrorCode` is added to the contract (`openapi.yaml`, the code enum and the endpoints that return it), to `ErrorCode` and to the frontend message table in the same change.
- Validation of request shape (blank, too long, malformed) is Bean Validation on the request record and answers `400 BAD_REQUEST`. Business validation is in the domain and answers `422` or the specific code.

## 4. Security basics (apply to every change)

- ShareWise has no passwords: no password, password hash or BCrypt anywhere in the code, logs or database. Sign-up, sign-in and token issuing belong to Keycloak (design 2026-10-30).
- Every endpoint except `/actuator/health` needs a valid Keycloak access token (`Authorization: Bearer`, checked by the resource server: signature, issuer, expiry) and resolves the caller through `CurrentUser`; the caller's membership is checked through the group port (`requireMember`) before any group data is read or written. A missing or refused token is a 401 `UNAUTHENTICATED`, never a 500.
- The browser never sees a token: the tokens live only in the httpOnly cookie of the Next.js server, which forwards the access token to the backend. Tokens are never logged.

## 5. Documentation

| Document | Purpose | Owner rule |
|---|---|---|
| `docs/api/openapi.yaml` | the HTTP contract | changed first, in its own commit |
| `docs/conventions/*` | these rules | changed together with the code that breaks them |
| `docs/2026-…-design.md`, `…-plan.md` | design decisions and plans for a piece of work | written before the work, not edited afterwards except to mark a decision changed |
| `RUNNING.md` | how a stranger starts, tests and demos the app | updated in the same commit as any change to setup or commands |
| `docs/domain/ShareWise_Domain_Model.drawio` | the domain diagram shown to the professor | updated in the same change as a rename or new concept; a code name the diagram lacks is a defect in one of the two |

Write documents in English, with headings that can be linked to, tables for comparisons, and commands in fenced blocks that can be copied. State assumptions as assumptions ("(call)" for a decision taken without asking).

## 6. Git

- **Branches:** `main` is always green. Work happens on `feature/<what>`, `fix/<what>`, `refactor/<what>` or `chore/<what>` and is merged with `git merge --no-ff` and a merge commit `merge: <branch> (<one line>)`. No direct commits to `main` except documentation fixes.
- **Commit format:** `<type>(<scope>): <imperative subject, lowercase, no full stop, at most 72 characters>`.
  - Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `contract` (a change to `openapi.yaml`, always on its own).
  - Scopes: `account`, `group`, `expense`, `settlement`, `notification`, `shared`, `frontend`, `e2e`, `build`, `devsetup` (the `.claude/` and `.githooks/` setup).
  - Body (optional): why, not what. Trailer on assisted commits: `Co-Authored-By: Claude <model> <noreply@anthropic.com>`, naming the model that did the work (e.g. `Claude Opus 5.5`).
- **Gates:** `.githooks/pre-commit` and `pre-push` (see `docs/devsetup/README.md`) run on every commit and push once enabled with `git config core.hooksPath .githooks`. Never `--no-verify`; change the gate in its own commit if it is wrong.
- **One concern per commit.** A rename and a behaviour change are two commits. Every commit builds and passes its own tests.
- Never commit secrets, `.env.local`, build output, `node_modules`, or generated reports (`test-results/`, `playwright-report/`). `.env.example` is committed.
- Tags mark hand-ins: `ex4`, `ex5`, … on the commit that was submitted. A tag is never moved after submission.
- Keep existing line endings: the repo mixes CRLF and LF, each file keeps the one it was created with. The commit gate refuses a commit that flips an existing file (`.githooks/line-endings.mjs`); `sed -i` and some editors strip carriage returns, so make edits with the edit tools and check `git diff --stat`.

## 7. Keep it simple (clean code, not clever code)

The aim is code a reader follows in one pass, with each rule in one place. This is a study project with a few users per group: do not build for what cannot happen, and do not abstract for what has two uses.

- **One place per rule.** A business rule lives once, in the domain. A filter or condition written in three places becomes one method (`Group.activeMemberships()`). A rule that really is checked in two layers needs a one-line comment saying why (the SQL of the open-invitation list repeats `Invitation.statusAt` so a list does not load whole groups, and a test pins the boundary).
- **Rule of three.** Extract when the same logic appears a third time or when two copies must always change together. Two similar lines, a one-line route handler, a `vi.mock(...)` line: leave them. An abstraction used once is a cost, not a gain.
- **Do not build for cases that cannot happen.** No check for a state an earlier step already guarantees, no defence or test for an operation that does not exist yet (park it with the trigger that brings it back, see `/edge-case-probe`). Edge cases only when they can really happen in the app.
- **The simplest mechanism that holds.** Prefer the lock, constraint or rule we already have over a new table, index, job, retry or layer. A mechanism is justified by the harm it prevents, not by how thorough it looks.
- **Use what exists.** Errors go through `ErrorBanner` and `messageFor`, loading through `useApi`, backend calls through `api<T>()` and `proxyToBackend`; do not write the same markup or handling again next to them.
- **Clear beats short.** Steps in the order they run, each named for what it does; one reason to change per method; comments say why. A refactor that makes the code harder to follow is not a gain.
- **Tests follow the same rule.** Each rule is tested once, at the lowest level that can show it (document 02); set-up that repeats three times becomes a helper, declarative boilerplate stays.
- **The review question:** would a reader stumble, or would a change have to be made in two places? If neither, leave it.
