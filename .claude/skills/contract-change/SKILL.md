---
name: contract-change
description: Change the ShareWise HTTP interface the contract-first way - openapi.yaml in its own commit, lint, regenerate the frontend types, then backend records/controller, error codes in all three places, frontend route handler and UI, tests on both sides. Use for any new or changed endpoint, field, status code or ErrorCode.
---

# Contract change (contract first)

Why: the contract is the agreement between backend and frontend (and later between services). Changing it first, alone, means both sides build against the same thing, the frontend can work against the Prism mock before the backend exists, and the change is visible in history as one decision.

## Steps
1. **Agree the change.** Say in a few lines which paths, fields, status codes and `ErrorCode`s change and why, using diagram names. A breaking change (renamed or removed field, new required field) needs the user's yes.
2. **Edit `docs/api/openapi.yaml`:** diagram names for JSON fields, money as decimal strings with two digits, every error response with its `code`, an example for each request and response (the mock serves them).
3. **Lint:** `npx @redocly/cli lint docs/api/openapi.yaml`.
4. **Commit the contract alone:** `contract(<scope>): <subject>`. The commit gate refuses it mixed with other files.
5. **Frontend types:** `cd frontend && npm run gen:api`; commit `src/api/schema.d.ts` with the frontend code that uses it (step 8) or on its own as `chore(frontend): regenerate api types`.
6. **New `ErrorCode`?** Add it to the Java enum (`shared`), to the domain or service that throws it, and its sentence to `frontend/src/lib/errors.ts` (`messageFor`). All three in the same change set.
7. **Backend:** request/response records in `web` named `<Operation>Request` / `<Thing>Response`, the controller method, the service operation (use `/backend:domain-operation` for a new aggregate operation). Controller test: status codes, JSON field names exactly as the contract, problem `code`s, 401/403/404.
8. **Frontend:** the route handler under `src/app/api/...` mirroring the backend path (`await connection()`, `proxyToBackend`), the call through `api<T>()` with the generated type, the UI; route-handler and component tests.
9. **Run the gates** (`node .githooks/gate.mjs pre-push < /dev/null` or just push) and check `RUNNING.md` if the demo flow changed.

Commit order: contract → backend (feat/test) → frontend (feat/test). Each commit builds on its own.
