# 04 Frontend (Next.js, TypeScript)

Stack: Next.js (App Router, `src/` layout), React, Tailwind, Vitest + Testing Library, Playwright. Formatting is whatever `npm run lint` accepts; there is no second formatter.

## 1. Folders and file names

```
src/
  app/                 routes only: page.tsx, layout.tsx, route.ts (no business logic)
    api/<endpoint>/route.ts      one route handler per backend endpoint
  components/          React components, one per file, PascalCase.tsx, test beside it (Name.test.tsx)
  lib/                 plain TypeScript: api.ts, errors.ts, money.ts, session.ts, useApi.ts …
  api/schema.d.ts      GENERATED from docs/api/openapi.yaml (npm run gen:api); never edited by hand
e2e/                   Playwright specs (*.spec.ts)
```
- Components: `PascalCase.tsx`, one exported component per file, named like the file. Hooks: `useThing.ts`. Plain modules: `camelCase.ts`.
- Route handlers mirror the backend path (`/api/groups/{groupId}/expenses` → `src/app/api/groups/[groupId]/expenses/route.ts`), each starts with `await connection()` and forwards through `proxyToBackend`.

## 2. API access and types

- **Only `api<T>(path)`** (`lib/api.ts`) talks to the backend from the browser; **only `proxyToBackend`** (`lib/backend.ts`) talks to it from a route handler. No other `fetch` of the backend, no CORS.
- Types come from the contract: `components["schemas"]["Expense"]`. Do not hand-write a type that the contract has.
- Field names in the UI are the contract's (= the diagram's): `paidBy`, `totalAmount`, `participantId`, `owedShare`, `netBalance`. No renaming in components.
- The session lives in the httpOnly cookie `sw_session`, set and cleared by the `/api/auth/*` route handlers. Components never read or write it.

## 3. Money

Amounts in the API are decimal strings (`"900.00"`). All parsing, formatting and arithmetic for them lives in **`lib/money.ts`** and nowhere else: user input → `parseEuro`, display → `formatEuro`, share previews → integer-cent helpers inside that file. No `parseFloat`, no `Number(amount)`, no floating point on money, no `toFixed` outside `money.ts`. Tests: `0,10`, `19.99`, a thousand separator is rejected, three decimals are rejected.

## 4. Errors and loading

- Every failed call ends in `ApiError`. Display text comes from `messageFor(error)` (`lib/errors.ts`), which maps each contract `code` (plus `BACKEND_UNREACHABLE`, `NETWORK_ERROR`) to a sentence and falls back to the server `detail`. Adding a contract `ErrorCode` means adding its message there.
- A page never renders blank on failure: errors show the `ErrorBanner`. Loading shows "Loading…".
- A `401` anywhere sends the visitor to `/login?next=<path>`; `next` is only ever used through `safeNext`.
- Balances are eventually consistent: after recording an expense the balances view re-fetches and re-checks for a short, bounded time. Never assume the new balance is there immediately.

## 5. Components

- Client components say `"use client"` on the first line; keep server components (pages, layouts) free of state and effects.
- State: local `useState`; server data through `useApi`. No global state library.
- Forms are controlled, labelled (`<label>` text is what Playwright and screen readers use), validate on submit, and disable the submit button while busy.
- Styling with Tailwind utility classes; a layout must work at phone width.
- Text in the UI is English, sentence case, and uses the glossary: "group", "expense", "paid by", "split equally between", "balance". Not "payer", "bill" or "user" for a group participant.
- No `console.log`, no commented-out code, no `any` (use `unknown` and narrow), no `// @ts-ignore`.

## 6. Tests

| Level | Tool | Rule |
|---|---|---|
| Unit (`lib/*`) | Vitest | pure functions: money, errors, `safeNext`, session cookie |
| Component | Vitest + Testing Library | one file per component; mock `@/lib/api`; query by role and label, not by class or test id; assert the message a user reads |
| Route handler | Vitest (`@vitest-environment node`) | `fetch` mocked; assert target URL, method, forwarded headers and the cookie handling |
| Journey | Playwright (`e2e/`) | fresh users per run (unique emails); no fixed sleeps; assertions retry |

Test names are sentences about behaviour (`showsAReadableMessageForWrongCredentialsAndStaysOnThePage`). Run: `npm run lint && npm run typecheck && npm test && npm run build`, and `npm run e2e` with the backend running.

## 7. Dependencies and tooling

- A new dependency needs a reason in the commit message. Prefer the platform and what is already installed.
- `package-lock.json` is committed and changed only by `npm install <pkg>` / `npm ci`. On Windows `npm install` can drop optional platform packages from the lock file: check `git diff package-lock.json` before committing and never commit a lock file that lost other platforms' entries.
- `typecheck` runs `next typegen` first, so a fresh checkout works.
