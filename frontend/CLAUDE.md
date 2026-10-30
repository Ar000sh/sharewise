# Frontend (Next.js)

Next.js 16 (App Router, `src/`), React 19, Tailwind 4, Vitest + Testing Library, Playwright. The browser talks only to the Next.js route handlers, which forward to the backend (`proxyToBackend`); the session token lives in an httpOnly cookie.

## Before you change code
- Next.js 16 differs from what you know (see the block below): read the matching guide in `node_modules/next/dist/docs/` first.
- Types come from the contract: `src/api/schema.d.ts` is generated (`npm run gen:api`) and never edited by hand. A new or changed endpoint starts with `/contract-change`.
- Money only through `src/lib/money.ts`; errors only through `ApiError` + `messageFor`; backend calls only through `api<T>()` and `proxyToBackend`.

## Commands (in `frontend/`)
```
npm run lint && npm run typecheck && npm test && npm run build   # what the push gate runs
npm run e2e                                                      # Playwright; backend + Postgres must run
npm run gen:api                                                  # after a contract change
```
On Windows, check `git diff package-lock.json` after `npm install`: the commit gate refuses a lock file that lost other platforms' entries.

@AGENTS.md

## The rules (binding)
@../docs/conventions/04_FRONTEND.md
