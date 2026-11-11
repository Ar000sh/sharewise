# Live group views Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every open group page converges to the server state within about 10 s of any member's change, without a backend change, and no form records money for a person the user did not choose.

**Architecture:** Server data moves from the hand-written `useApi` (useState + useEffect) to TanStack Query behind the same `useApi` name. The open group page polls its mounted queries every 10 s and refetches on focus; a write marks its group changed, which invalidates the group's queries and starts the existing bounded look-again for the ledger lag. Two forms are made safe for props that change while the user types.

**Tech Stack:** Next.js 16, React 19, `@tanstack/react-query` 5, Vitest + Testing Library, Playwright.

**Spec:** `docs/2026-11-11-live-group-views-design.md` (decisions L1 to L6, D1, D2, failure modes F1 to F10).

## Global Constraints

- Frontend only. No change to `backend/`, `docs/api/openapi.yaml`, migrations or the diagram.
- All work in `frontend/` of the worktree `.claude/worktrees/live-group-views`, branch `feature/live-group-views`. First time: `cd frontend && npm install`.
- Poll interval `10_000` ms, only for the queries of the open group page; paused while the browser tab is hidden (`refetchIntervalInBackground` stays `false`).
- `staleTime: 0`, `refetchOnWindowFocus: true`, no retry for an `ApiError` with status 400 to 499.
- Query keys are the API path split into segments: `"/groups/g1/balances"` → `["groups", "g1", "balances"]`.
- Backend calls only through `api<T>()`; a 401 is already handled there (merged `fda920f`), no code here handles 401.
- `useLookAgain` delays stay `[500, 1000, 2000, 4000]`.
- Button label "Select everyone" (call). Expense form: only the recorder ticked by default, ticked and disabled (R18).
- Waiver form rule: it follows the plan until the user touches it (chooses a debtor, types an amount or a reason); after that it stays with that debtor; if that debtor leaves the plan, no debtor is selected and submit says "Please choose who owes you." After the user's own successful waiver it follows the plan again.
- Test names are sentences, `// given // when // then`, query by role and label (`.claude/rules/frontend-tests.md`).
- Commits `<type>(frontend|e2e): ...`, one concern each, trailer `Co-Authored-By: Claude <model> <noreply@anthropic.com>`. Check `git diff package-lock.json` (no lost platforms) and `git diff --stat` (no line-ending flips) before each commit.
- Gate per task: `npm run lint && npm run typecheck && npm test`; `npm run build` at the end of tasks 1 and 3.

## Review Focus

1. A poll fails once (train, laptop wakes up) while a form is half filled: the page must stay, keep the tab and the typed text, and show the banner over the old data. Today `GroupDetail.tsx:33` replaces the whole page on any error. Test in Task 3 (`aFailedPollKeepsThePageAndShowsTheBanner`).
2. A poll returns while the user types in "Add expense", "I paid" or "Waive": typed values stay (forms keep their own state; unchanged data keeps identity). Test in Task 5 (`aMemberWhoJoinsWhileTheFormIsOpenIsNotInTheSplit` also asserts the typed title stays).
3. The waiver form silently changing person, both when the user chose and when they relied on the first entry. Tests in Task 4.
4. The person who recorded an expense switches to Settle up within 4 s: the plan must look again there too, not only on Balances. Test in Task 2 (`afterAnExpenseTheSettleUpTabLooksAgainWhenOpened`).
5. The creditor confirms in the bell while their Balances tab is open: Balances must look again (today only Settle up listens). Test in Task 2 (`answeringAPaymentMarksItsGroupChanged` plus the Balances look-again test reading the same marker).

---

### Task 1: TanStack Query behind `useApi`

**Files:**
- Modify: `frontend/package.json`, `frontend/package-lock.json` (`npm install @tanstack/react-query@5`)
- Create: `frontend/src/lib/queryClient.ts`
- Create: `frontend/src/components/QueryProvider.tsx`
- Modify: `frontend/src/app/layout.tsx` (wrap `CurrentUserProvider` in `QueryProvider`)
- Modify: `frontend/src/lib/useApi.ts`
- Create: `frontend/src/lib/renderWithQueryClient.tsx` (test helper)
- Create: `frontend/src/lib/useApi.test.tsx`
- Modify (mechanical, `render(` → `renderWithQueryClient(` for every render of a component that uses `useApi`): `src/components/Balances.test.tsx`, `GroupDetail.test.tsx`, `GroupList.test.tsx`, `SettleUpTab.test.tsx`, `WaiveDebtForm.test.tsx`
- Modify: `docs/conventions/04_FRONTEND.md` section 5, line "State: local `useState`; server data through `useApi`. No global state library."

**Interfaces:**
- Produces:
  - `makeQueryClient(): QueryClient` in `lib/queryClient.ts` (the defaults of Global Constraints).
  - `QueryProvider({ children }: { children: React.ReactNode })` in `components/QueryProvider.tsx`, `"use client"`, one client per browser via `useState(makeQueryClient)`.
  - In `lib/useApi.ts`: `export const POLL_MS = 10_000;` `export function queryKeyOf(path: string): string[]`; `export function useApi<T>(path: string, options?: { poll?: boolean }): { data: T | undefined; error: unknown; reload: () => Promise<void> }`. `error` is `undefined` when there is none. `reload` invalidates `queryKeyOf(path)` and keeps its identity across renders (`useCallback` on the client and `path`), because `useLookAgain` restarts its timers when `reload` changes (`useLookAgain.ts:36`). `poll: true` sets `refetchInterval: POLL_MS`.
  - In `lib/renderWithQueryClient.tsx`: `export function testQueryClient(): QueryClient` (retry off) and `export function renderWithQueryClient(ui: React.ReactElement, client?: QueryClient): RenderResult & { client: QueryClient }`.

- [ ] **Step 1: Install** `npm install @tanstack/react-query@5`, then `git diff package-lock.json` shows only additions for `@tanstack/*` and no removed platform packages.
- [ ] **Step 2: Write the failing tests** in `src/lib/useApi.test.tsx` (mock `@/lib/api` as the other tests do):
  - `theQueryKeyIsThePathInSegments`: `expect(queryKeyOf("/groups/g1/balances")).toEqual(["groups", "g1", "balances"])`.
  - `loadsThePathAndReturnsTheData`: api resolves `[1]` → `data` becomes `[1]`, `api` called with `"/groups/g1/balances"`.
  - `aFailedCallIsTheErrorAndIsNotRetriedFor4xx`: api rejects `new ApiError(403, "NOT_A_MEMBER")` → `error` is that error, `api` called once.
  - `reloadKeepsItsIdentityBetweenRenders`: `renderHook` + `rerender()` → `result.current.reload` is the same function.
  - `reloadAsksAgain`: after `await act(() => result.current.reload())` `api` was called twice.
  - `pollAsksAgainEveryTenSeconds`: fake timers (`vi.useFakeTimers({ shouldAdvanceTime: true })`), `poll: true`, advance `POLL_MS` → 2 calls, advance again → 3.
  - `pollPausesWhileTheBrowserTabIsHidden`: `focusManager.setFocused(false)` (reset to `undefined` in `afterEach`), advance `2 * POLL_MS` → still 1 call.
  - `withoutPollNothingIsAskedAgain`: advance `2 * POLL_MS` → 1 call.
- [ ] **Step 3: Run** `npx vitest run src/lib/useApi.test.tsx` → FAIL (`queryKeyOf` not exported).
- [ ] **Step 4: Implement** `queryClient.ts`, `QueryProvider.tsx`, the layout wrap, `useApi` on `useQuery({ queryKey: queryKeyOf(path), queryFn: () => api<T>(path), refetchInterval: options?.poll ? POLL_MS : false })`, and `renderWithQueryClient.tsx`.
- [ ] **Step 5: Run** `npx vitest run src/lib/useApi.test.tsx` → PASS.
- [ ] **Step 6: Wrap the existing component tests** listed above; run `npm test` → all PASS (behaviour unchanged).
- [ ] **Step 7: Convention 04 section 5:** the line becomes "State: local `useState`; server data only through `useApi`, which is TanStack Query (`lib/useApi.ts`, client defaults in `lib/queryClient.ts`); no client state library."
- [ ] **Step 8: Gate** `npm run lint && npm run typecheck && npm test && npm run build` → green.
- [ ] **Step 9: Commit** `feat(frontend): server data through TanStack Query behind useApi`, body with the dependency reason: "one cache keyed by the API path, invalidation instead of hand-wired reloads, refetch on focus and polling for the open group page (design 2026-11-11 L1)".

### Task 2: A write marks its group changed

**Files:**
- Create: `frontend/src/lib/useGroupChange.ts`, `frontend/src/lib/useGroupChange.test.tsx`
- Modify: `frontend/src/components/GroupDetail.tsx` (remove `lastRecordedAt`, `reloadExpenses`; `onRecorded` and `onPromoted` call `markChanged`)
- Modify: `frontend/src/components/BalancesTab.tsx` (prop `lastRecordedAt` removed; reads `lastChangedAt` from the hook)
- Modify: `frontend/src/components/SettleUpTab.tsx` (remove `changedAt` state and the `PAYMENT_ANSWERED` effect; `onPaid` and `afterWaive` call `markChanged`; `useLookAgain(lastChangedAt, reload)`)
- Modify: `frontend/src/components/InboxBell.tsx` (confirm and reject call `markGroupChanged(client, payment.groupId)` instead of `announcePaymentAnswered`)
- Delete: `frontend/src/lib/paymentAnswered.ts`
- Modify tests: `Balances.test.tsx` 77-117 (seed the marker instead of the prop), `SettleUpTab.test.tsx` 147-160 (replace), `InboxBell.test.tsx` 197-230 (replace; and every `render(` of `InboxBell` becomes `renderWithQueryClient(`, because the bell now uses `useQueryClient`), `GroupDetail.test.tsx` (add one)

**Interfaces:**
- Consumes: `queryKeyOf`, `useApi`, `testQueryClient`, `renderWithQueryClient` (Task 1).
- Produces in `lib/useGroupChange.ts`:
  - `export function lastChangedKey(groupId: string): string[]` → `["groups", groupId, "lastChangedAt"]`.
  - `export function markGroupChanged(client: QueryClient, groupId: string): void` → `client.setQueryData(lastChangedKey(groupId), Date.now())`, then `client.invalidateQueries({ queryKey: ["groups", groupId] })`.
  - `export function useGroupChange(groupId: string): { lastChangedAt: number | undefined; markChanged: () => void }` → reads the marker with `useQuery({ queryKey: lastChangedKey(groupId), queryFn: () => Date.now(), enabled: false })` (disabled: it is never fetched, only set; disabled queries are not refetched by the invalidation); `markChanged` stable via `useCallback`.

- [ ] **Step 1: Write the failing tests**
  - `useGroupChange.test.tsx`: `markingAGroupChangedSetsTheTimeAndRefetchesItsQueries` (a `useApi("/groups/g1/balances")` hook and `useGroupChange("g1")` in one client; after `act(() => markGroupChanged(client, "g1"))` the balances `api` call count grew and `lastChangedAt` is a number); `anotherGroupIsNotRefetched` (a `useApi("/groups/g2/balances")` keeps one call).
  - `Balances.test.tsx`: the three look-again tests seed `client.setQueryData(lastChangedKey("g1"), Date.now())` on a `testQueryClient()` passed to `renderWithQueryClient` instead of the prop; assertions unchanged.
  - `SettleUpTab.test.tsx`: replace `looksAtThePlanAgainWhenThePaymentBellAnswersAPayment` with `looksAtThePlanAgainWhenItsGroupIsMarkedChanged` (`act(() => markGroupChanged(client, "g1"))` → plan calls grow within 3 s).
  - `InboxBell.test.tsx`: replace the two `PAYMENT_ANSWERED` tests with `answeringAPaymentMarksItsGroupChanged` (after confirm `client.getQueryData(lastChangedKey(payment.groupId))` is a number) and `failedAnswerMarksNothing` (403 on confirm → alert shown, marker `undefined`).
  - `GroupDetail.test.tsx`: `afterAnExpenseTheSettleUpTabLooksAgainWhenOpened` (record an expense as in `tellsTheUserThatBalancesFollowAfterAnExpenseWasRecorded`, click tab "Settle up" → `/groups/g1/settlement-plan` is asked more than once within 3 s). `tellsTheUserThatBalancesFollowAfterAnExpenseWasRecorded` stays green.
- [ ] **Step 2: Run** `npm test` → the new and changed tests FAIL.
- [ ] **Step 3: Implement** `useGroupChange.ts`, then the four components; delete `paymentAnswered.ts`; `grep -rn "paymentAnswered\|PAYMENT_ANSWERED\|lastRecordedAt" src` → nothing.
- [ ] **Step 4: Run** `npm test` → all PASS.
- [ ] **Step 5: Gate** `npm run lint && npm run typecheck && npm test`.
- [ ] **Step 6: Commit** `feat(frontend): a write marks its group changed, every tab looks again`.

### Task 3: The open group page polls

**Files:**
- Modify: `frontend/src/components/GroupDetail.tsx` (the two `useApi` calls with `{ poll: true }`; line 33 `if (error) return` becomes `if (error && !group) return`, and the banner is shown above the page when `error` is set while `group` is there)
- Modify: `frontend/src/components/BalancesTab.tsx`, `frontend/src/components/SettleUpTab.tsx` (`{ poll: true }`)
- Modify: `frontend/e2e/mallorca.spec.ts` (journey, see Step 5)
- Modify: `docs/conventions/04_FRONTEND.md` section 4, the eventual-consistency line
- Test: `frontend/src/components/GroupDetail.test.tsx`

**Interfaces:**
- Consumes: `useApi(path, { poll: true })`, `POLL_MS` (Task 1).

- [ ] **Step 1: Write the failing tests** in `GroupDetail.test.tsx`:
  - `theOpenGroupPageAsksAgainEveryTenSeconds`: fake timers, page rendered, advance `POLL_MS` → `/groups/g1` and `/groups/g1/expenses` each asked twice.
  - `aFailedPollKeepsThePageAndShowsTheBanner`: first answers succeed; switch to tab "Balances"; next `/groups/g1` call rejects `new ApiError(0, "NETWORK_ERROR")`; advance `POLL_MS` → the heading "Mallorca 2027" and the selected tab "Balances" are still there and an `alert` is shown.
- [ ] **Step 2: Run** `npx vitest run src/components/GroupDetail.test.tsx` → FAIL.
- [ ] **Step 3: Implement** the `poll` options and the `error && !group` guard.
- [ ] **Step 4: Run** `npm test` → PASS.
- [ ] **Step 5: Journey** in `e2e/mallorca.spec.ts`: right after Alice confirms Carol's payment (line ~124) and before Carol's reload loop, assert that Carol's open Settle up tab updates on its own: `await expect(carolOwes).toHaveCount(0)` (the "You owe" region disappears; retries within the 20 s expect timeout). Replace Alice's reload loop at lines ~134-139 by clicking the "Settle up" tab and `await expect(owedToAlice.getByRole("listitem")).toHaveCount(1)`; delete the comment "The plan is only fetched when the tab opens, so she reloads until the booking has arrived". Run when a backend is available (`npm run e2e`); CI runs it on the pull request in any case.
- [ ] **Step 6: Convention 04 section 4:** "Balances are eventually consistent: after an own write the open tab looks again for a short, bounded time (`useLookAgain`), and the open group page asks again every 10 s and on focus. Never assume the new balance is there immediately."
- [ ] **Step 7: Gate** `npm run lint && npm run typecheck && npm test && npm run build`.
- [ ] **Step 8: Commit** `feat(frontend): the open group page asks again every 10 seconds` (code, unit tests, 04); then `test(e2e): an open settle-up tab follows a confirmed payment without reload` (journey alone).

### Task 4: The waiver form never switches person (D2)

**Files:**
- Modify: `frontend/src/components/WaiveDebtForm.tsx` (lines 31-47 selection logic, the `select`, `submit` success branch)
- Test: `frontend/src/components/WaiveDebtForm.test.tsx`

**Interfaces:**
- Consumes: nothing new; the component keeps its props `{ groupId, waivableEdges, onWaived? }`.

- [ ] **Step 1: Write the failing tests** (render `SettleUpTab` as the file does; the plan sequence via `serve(..., [planA, planB])`, the second plan delivered by `act(() => markGroupChanged(client, "g1"))`):
  - `aChosenDebtorWhoLeavesThePlanLeavesNoSelectionAndNothingIsSent`: Alice selects "Bob", types amount `50`; the plan reloads without Bob → the debtor select shows the placeholder "Choose who owes you"; clicking "Waive debt" shows "Please choose who owes you." and `waiveCalls()` has length 0.
  - `aDebtorShownByDefaultStaysOnceTheUserTypes`: Alice does not touch the select (first entry Carol), types reason "Gift"; the plan reloads with David first and Carol second → the select still shows "Carol".
  - `untouchedTheFormFollowsThePlan`: no input; the plan reloads with David first → the select shows "David".
  - Existing `afterAFullWaiveTheFormOffersTheNextDebtorAndCanBeSubmitted` and `afterAPartialWaiveTheAmountFollowsTheNewOpenDebt` stay green.
- [ ] **Step 2: Run** `npx vitest run src/components/WaiveDebtForm.test.tsx` → the three new tests FAIL.
- [ ] **Step 3: Implement** the rule of Global Constraints: the first touch (choose, amount, reason) pins `chosenDebtorId` to the shown debtor; `selectedEdge` is the chosen one when pinned (possibly none), else `waivableEdges[0]`; with no selection the select renders a disabled placeholder option `value=""` "Choose who owes you"; a successful own waiver resets the pin to `undefined`.
- [ ] **Step 4: Run** `npm test` → PASS.
- [ ] **Step 5: Gate** `npm run lint && npm run typecheck && npm test`.
- [ ] **Step 6: Commit** `fix(frontend): a waiver never switches to another debtor while the form is open`.

### Task 5: The expense form starts with only the recorder (D1)

**Files:**
- Modify: `frontend/src/components/ExpenseForm.tsx` (lines 38, 42, 46-48 `excluded` → `included`; fieldset lines 112-124 gets the button)
- Modify: `frontend/src/components/Expenses.test.tsx`
- Modify: `frontend/e2e/mallorca.spec.ts` `addExpense` (line 39-44)
- J4 step 2 in the requirements file (it was `work/ShareWise_Requirements.md` in the FAVS folder; now `docs/domain/ShareWise_Requirements.md`)

**Interfaces:**
- Consumes: nothing new; props unchanged `{ groupId, members, currentUserId, onRecorded }`.

- [ ] **Step 1: Write the failing tests** in `Expenses.test.tsx`:
  - Rename and change `formDefaultsPayerToCurrentUserAndParticipantsToAll` → `formDefaultsPayerAndParticipantsToTheCurrentUser`: Bob checked and disabled, Alice and Carol not checked.
  - `selectEveryoneTicksAllMembers`: click button "Select everyone" → all three checked; fill "Taxi", "10" → preview `["Alice3,34 €", "Bob3,33 €", "Carol3,33 €"]` (this replaces the set-up of `showsSharePreviewPerParticipant`, which clicks "Select everyone" first).
  - `aMemberWhoJoinsWhileTheFormIsOpenIsNotInTheSplit`: click "Select everyone", type title "Hotel"; `rerender` with a fourth member Dave → Dave's checkbox not checked, preview has three rows, title still "Hotel".
  - `submitPostsExpenseAndRefreshesList`: instead of unticking Carol, tick Alice → `participantIds: ["u1", "u2"]`.
  - `theRecorderCannotUntickThemselvesButOthersCanBeUnticked`: start with "Select everyone", then untick Alice and Carol; assertions unchanged.
- [ ] **Step 2: Run** `npx vitest run src/components/Expenses.test.tsx` → FAIL.
- [ ] **Step 3: Implement** `const [included, setIncluded] = useState<string[]>([currentUserId])`, `participants = members.filter((m) => included.includes(m.userId))`, the toggle on `included`, and `<button type="button">Select everyone</button>` inside the fieldset after the legend setting `members.map((m) => m.userId)`. The recorder's checkbox stays `disabled` and checked (R18).
- [ ] **Step 4: Run** `npm test` → PASS.
- [ ] **Step 5: e2e** `addExpense` clicks `page.getByRole("button", { name: "Select everyone" })` before "Add expense" (the journey splits hotel and dinner between all three).
- [ ] **Step 6: Gate** `npm run lint && npm run typecheck && npm test`.
- [ ] **Step 7: Commit** `feat(frontend): an expense starts with only the recorder, one click selects everyone` (component, tests and the e2e helper together: the helper breaks without the change). Body: "J4 step 2 changed with the user on 11.11 (design D1): a member who joins while the form is open can never slip into the split."
- [ ] **Step 8: Requirements file:** J4 step 2 "participants (default: everyone)" → "participants (default: only me; one click selects everyone)". *(Done differently: the file was in the FAVS folder, outside this repo; at the user's request it moved into `docs/domain/` and J4 is its own commit.)*

### Task 6: Finish

- [ ] **Step 1:** Design doc `docs/2026-11-11-live-group-views-design.md`: F7 row gets "`GroupDetail` kept the page only after the `error && !group` guard (plan Task 3)"; F2 and D1 wording checked against the built form. Commit `docs(frontend): design notes match the built live group views`, together with the CATALOG row already in the working tree (`docs/devsetup/CATALOG.md`) in its own `docs(devsetup): ...` commit.
- [ ] **Step 2:** `grep -rn "No global state library\|lastRecordedAt\|PAYMENT_ANSWERED\|reloads until the booking" docs frontend/src frontend/e2e` → nothing.
- [ ] **Step 3:** Full gate `npm run lint && npm run typecheck && npm test && npm run build`; `npm run e2e` if a backend runs (else CI).
- [ ] **Step 4:** Look at the expense form and the waiver placeholder in light and dark mode (04 section 5).
- [ ] **Step 5:** `/review-branch`, then `/review-doc`, then the pull request (`gh pr create`), CI `ci-passed` green.
