# Review: <feature>

Branch `<branch>`. Before: <one sentence: how it worked or what was missing>. Now: <one sentence: what the user can do>. New: <new modules, tables or concepts, a few words>.

## Features and where to start

Start at the entry point and follow the path yourself: **controller → service → domain (the rules) → repository**, then the test. Backend paths are under `backend/src/main/java/de/hsh/favs/sharewise/`, frontend under `frontend/src/`.

| Feature | Start at (backend) | Frontend | Test to read |
|---|---|---|---|
| **<what the user does>** | `<module>/web/<Controller>.<method>` `<METHOD /path>` → `<Service>.<method>` → `<Aggregate>.<method>` | `components/<Component>.tsx` | `<Test>` |
| **<an event reaction>** | listener `<module>/application/<Event>Listener` → `<Service>.<method>` | – | `<Test>` |

Backend entry points are **controllers** (HTTP) and **listeners** (events). Frontend entry points are components; each calls a Next.js route handler in `app/api/`, which forwards to the backend controller.

```mermaid
sequenceDiagram
  <the main flow in 6 to 8 lines>
```

## Know these before you read

- **<decision>.** <the reason in one clause>
- (at most six)
- Full reasons and history: `docs/<date>-<topic>-design.md` and `git log`.

## Diff and run

```sh
D="git diff -w --ignore-cr-at-eol"     # hides line-ending noise
$D main...HEAD -- docs/api/openapi.yaml                       # the contract: a good first read
$D main...HEAD -- backend/src/main/java/de/hsh/favs/sharewise/<module>   # one area at a time
git show <commit>                                             # one commit with its message
cd backend && ./mvnw -q verify      # Docker must run
cd frontend && npm test && npm run e2e
```

## Open

- <unverified, parked or waiting items, at most four>
