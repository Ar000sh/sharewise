---
paths:
  - "frontend/**/*.test.ts"
  - "frontend/**/*.test.tsx"
  - "frontend/e2e/**"
---

# Writing frontend tests

Levels and tools are in `docs/conventions/04` section 6; the shared style is the one of the backend (`docs/conventions/02` section 2):
- Test names are sentences about behaviour, no `test` prefix (`showsAReadableMessageForWrongCredentialsAndStaysOnThePage`).
- Blocks `// given`, `// when`, `// then`; one reason to fail per test; assert what did **not** happen too (no request sent, no redirect).
- Components: mock `@/lib/api`, query by role and label (never by class or test id), assert the text a user reads.
- Route handlers: `@vitest-environment node`, `fetch` mocked; assert URL, method, forwarded headers and cookie handling.
- Playwright: fresh users with unique emails each run, no fixed sleeps (assertions retry), balances may lag an expense (eventual consistency).
- Money cases always include `0,10`, `19.99`, a rejected thousand separator and rejected three decimals.
