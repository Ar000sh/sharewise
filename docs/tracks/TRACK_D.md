# Track D: frontend

**Branch:** `track/d-frontend` · **Tasks:** plan **D1, D2, D3, D4** · **SYNC section:** Track D

**You own (only touch these):** `frontend/src/app/**` pages (but **not** `src/app/api/**`), `frontend/src/components/**`, new files under `frontend/src/lib/` (but not `backend.ts`, `api.ts`, `user-cookie.ts`), and your tests next to your code.

**You deliver:** groups list and create, group detail with members and invite link, join page, expense form and list, balances tab, error banner.

**Notes**
- **Work against the mock, no backend needed:** `sh docs/api/mock.sh` (Prism on :4010), then `BACKEND_URL=http://localhost:4010 npm run dev`. Prism returns the contract's examples. Type everything from `src/api/schema.d.ts` (`components["schemas"][...]`); it is generated from the contract, do not edit it. If it looks stale, tell the hub.
- Call the backend only through `api<T>(path)` from `src/lib/api.ts` (path = backend path like `"/groups"`). Never `fetch` the backend directly, never add CORS.
- Current user id (default payer, "is this me"): `api<User>("/me")`. The dev user switcher already exists in the layout.
- Money: the API uses cents. Write one helper `src/lib/money.ts` (`eurosToCents("19,99") === 1999`, `formatEuro(1999)`), no floating point arithmetic on amounts. Test `0,10`, `19.99`, `1.000,50`-style rejection or handling as you decide, and note it.
- Show server errors readably: map known problem `code`s to messages (e.g. `PAYER_NOT_IN_GROUP`, `ALREADY_MEMBER`, `INVITATION_EXPIRED`) and fall back to `detail`. A backend that is down (`BACKEND_UNREACHABLE`, or `NETWORK_ERROR`) must show an error banner, never a blank page.
- Balances are eventually consistent with expenses: after recording an expense, re-fetch balances (and consider a short re-fetch if the sum looks stale); do not assume instant update.
- Keep pages usable on a phone width. Styling is yours (Tailwind is set up); keep it simple.
- Component tests with Testing Library and `vi.spyOn(globalThis, "fetch")` or by mocking `@/lib/api`.

**Done checklist**
- [ ] D1–D4 tests green; `npm run lint && npm run typecheck && npm test && npm run build` all pass.
- [ ] The whole slice is clickable against Prism (create group, invite, join, add expense, balances).
- [ ] Done report written in the SYNC file.

**Prompt to paste into the session** (open Claude Code in `C:/Users/Saturn/Desktop/Studium/Master/ws_26_27/sharewise-track-d`):

> You are Track D (frontend) of the ShareWise Ex4 build. Read `docs/tracks/README.md` and `docs/tracks/TRACK_D.md`, then execute plan tasks D1–D4 from `docs/2026-10-24-sharewise-modulith-ex4.md` with test-driven development, running against the Prism mock. Run `npm install` in `frontend/` first. Follow the SYNC protocol in the README. Do not merge or push. Stop when the done checklist is complete and tell me what to review.
