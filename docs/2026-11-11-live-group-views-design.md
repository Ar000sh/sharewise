# Live group views: a query cache and polling of the open group page (design)

Date: 2026-11-11. Branch `feature/live-group-views` (from `main` at `fda920f`). Status: decided after `/design-review`; `/edge-case-probe` plan mode next.

## Why

Screens show values that were correct when they were loaded and are never told that the server changed them. The values on the server are right; the screen is wrong. Seen by the user: the bell showed "Alice confirmed your payment", the open Settle up tab kept the old plan.

How the frontend loads data today (`frontend/src/lib/useApi.ts`): a `useState` + `useEffect` hook per component, fetched on mount, `reload()` by hand. No shared cache, no refetch on focus, no polling (except the bell, every 30 s). Only the active tab of a group is mounted (`GroupDetail.tsx:57-89`), so switching tabs refetches; a tab that stays open is refreshed only by the current user's own writes (`onRecorded`, `onPaid`, `afterWaive`, `onPromoted`) and by the browser event `PAYMENT_ANSWERED` that the bell fires after the user answered a payment (`lib/paymentAnswered.ts`, listened to only by `SettleUpTab`).

### Where views go stale today (survey of every write endpoint in `openapi.yaml`)

| Write (who) | Views that change | Who is told today | Stale |
|---|---|---|---|
| Accept invitation (invitee) | Members tab, expense form members, expense list names (`ExpenseList.tsx:10` shows "Former member" for an unknown id), member count in the group list (`GroupList.tsx:32`) | inviter gets `MEMBER_JOINED`; nothing refreshes | every other member's open pages |
| Promote (admin) | promoted person's `isAdmin` (invite form, promote buttons); everyone's role badges | promoted person gets `MEMBER_PROMOTED`; nothing refreshes | everybody but the admin who clicked |
| Record expense (recorder) | expenses, balances, plan of everybody | participants and payer except recorder get `EXPENSE_RECORDED`; nothing refreshes | everybody but the recorder |
| "I paid" (debtor) | plan (`pendingAmount`, `payableAmount`, "Waive" button) of everybody | creditor sees the payment in the bell (state, no entry) | everybody but the debtor |
| Confirm / reject (creditor) | balances (confirm) and plan of everybody | debtor gets `PAYMENT_CONFIRMED` / `PAYMENT_REJECTED`; creditor's Settle up tab via `PAYMENT_ANSWERED`, creditor's Balances tab not | debtor, bystanders, creditor's Balances tab |
| Waive (creditor) | balances and plan of everybody | debtor gets `DEBT_WAIVED` | debtor and bystanders |
| Create group, invite, decline, mark read | no other member's view (no pending-invitation list exists) | — | none |

Stale data never makes money wrong: every write is validated by the server (a stale "Waive" gets 422, a stale admin UI gets 403 `NOT_AN_ADMIN`). The harm is a screen that contradicts the bell and a confusing refusal.

**Key observation:** notifications are addressed to one person, the views are group-wide (the balances table and the plan show everybody). Invalidating caches from the bell's notifications would fix the addressed person and leave every bystander stale. So the open group page must find out on its own.

## Decisions

| # | Decision | Why | Status |
|---|---|---|---|
| L1 | The frontend uses **TanStack Query** for server data. `useApi` keeps its name and its `{ data, error, reload }` shape and is reimplemented on `useQuery`. | One cache keyed by what the data is; invalidation instead of hand-wired reloads and the `PAYMENT_ANSWERED` event; refetch on focus for free. Standard production pattern (stale-while-revalidate). | user, 11.11 |
| L2 | **The open group page refetches its mounted queries every 10 s** (paused while the browser tab is hidden), plus refetch on window focus. No backend change. | Reaches bystanders as fast as an event-fed signal would, with no backend, contract, migration or diagram change, and every poll reads the real data, so a refetch that came too early is corrected by the next one. Cost: about three small GETs per 10 s per visible group page, negligible at a few users per group. | user, 11.11 (after design review) |
| L3 | **Parked: a per-group activity counter** fed by the existing events (a read model in `notification`, one upsert, not an aggregate; endpoint `GET /groups/{groupId}/activity`). | It saves requests but needs a migration, contract, endpoint and diagram delta, and has two gaps of its own: a poll that lands between the counter bump and the ledger booking stays stale in a quiet group, and the first value can already include a change. Its real value comes with push: at the messaging/SSE step the same per-group signal is what gets pushed. **Change driver** for P3. Never on `Group` (would need events from expense and settlement: the `group → settlement` cycle of `01` section 5.1). | parked, with trigger |
| L4 | **A 401 needs no code in this feature.** | Merged on `main` in `fda920f` (`fix/session-redirect`): `api()` sends the visitor to `/login?next=...` and still throws (`lib/api.ts`, `04` line 35). The 10 s poll ends because the page is left. | done |
| L5 | One branch for all of it. | The user's choice. | user, 11.11 |
| L6 | No new event "ledger booked". | The window between an event and its booking is milliseconds; the next 10 s poll corrects a refetch that came too early; the person who made the change keeps the bounded look-again. | user, 11.11 |

### What the design review changed (11.11, independent Opus reviewer)

The first draft had the activity counter (now L3). The reviewer showed that plain polling at 10 s reaches bystanders as fast with no backend work and without the counter's two gaps, and found six mistakes, all verified in the code and fixed below: removing `lastRecordedAt` would have broken "Balances update in a moment" (the expense is recorded on the Expenses tab, where Balances is not mounted); the 401 case was described wrongly (resolved by L4); `reload` must stay a stable reference or `useLookAgain` restarts its timers forever (`useLookAgain.ts:36`); the survey missed the member count in the group list; an open payment or waiver form disappears when someone else settles that debt (F6); the counter was called an aggregate but protects no invariant.

## Goals, non-goals

**Goals:** every open group view converges to the server state within about 10 s of any member's change, and at once when the browser tab gets focus; the person who made a change sees it as soon as the ledger has booked it, as today ("Balances update in a moment" keeps working). The bell keeps its 30 s poll, so it may tell about a change up to 30 s after the open tab already shows it, as today.

**Non-goals (parked, with the trigger):**
- Instant updates (push over SSE or WebSocket, with the counter of L3): when a view must update instantly, or at the messaging exercise.
- A live group list: the member count heals on mount and on focus; the list is not polled.
- A participant picker with search (a dialog to find and add people to an expense), suggested by the user: when exact or percentage splits (J4b, J4c) are built, because their picker needs an amount per person and should be designed once; or when groups grow beyond about ten people.
- A removed member's open page (it would get 403 every 10 s): remove member and leave have no endpoints yet; handle it when they are built.

**Anti-goal:** no second source of truth in the browser. The cache only holds server answers; no optimistic updates, no client-side balance arithmetic.

## Design

### Overview

```
any member writes ──► server (validates, commits, events book the ledger a moment later)

open group page ──► every 10 s while visible, and on focus ──► refetch its mounted queries
own write       ──► invalidate the group's queries at once
                    + bounded look-again (0.5 / 1 / 2 / 4 s) for balances and plan (ledger lag)
bell (30 s)     ──► after confirm / reject: mark the payment's group changed (accept needs nothing: it opens the group page, which fetches on mount)
```

### Frontend (the whole change)

- **Dependency:** `@tanstack/react-query` (reason in the commit message). A client `Providers` component with one `QueryClient` in `app/layout.tsx`. Defaults: `staleTime: 0` (show the cached value, refetch on mount), `refetchOnWindowFocus: true`, no retry for an `ApiError` with a 4xx status (a 401 has already left the page, a 403 or 404 will not heal by asking again).
- **Query keys** are the API path split into segments: `/groups/{id}/balances` → `["groups", id, "balances"]`. Invalidating `["groups", id]` reaches every query of that group (detail, expenses, balances, plan).
- **`useApi(path, options?)`** = `useQuery` on that key. `reload` is `invalidateQueries` of the key, wrapped in `useCallback` on the key, so it keeps its identity between renders (`useLookAgain` restarts its timers when `reload` changes). Unchanged answers keep their object identity (TanStack's structural sharing), so a poll that brings nothing new does not re-render a half-filled form.
- **Polling:** the queries under `GroupDetail` (group, expenses, the active tab's balances or plan) pass `refetchInterval: 10_000`; TanStack pauses it while the browser tab is hidden (`refetchIntervalInBackground` stays false). Other pages (group list) do not poll.
- **Own writes** invalidate the group prefix: `ExpenseForm`, `PaymentForm`, `WaiveDebtForm`, `MemberList` promote. The bell invalidates `["groups", payment.groupId]` after confirm or reject, *(The first draft also had `["groups"]` invalidated after accept and after creating a group. Dropped as unnecessary: `staleTime: 0` refetches the group list when its page mounts, and both actions navigate to the group page.)*
- **Ledger lag for the person who wrote:** the time of the last change of a group is kept in the query cache next to its data (`lib/useGroupChange.ts`: `markGroupChanged(client, groupId)` sets it and invalidates the group's queries; `useGroupChange(groupId)` reads it and gives a stable `markChanged`). The expense form, "I paid", the waiver, promote and the bell's confirm and reject mark the group changed. Each tab runs `useLookAgain(lastChangedAt, reload)` when it mounts, so the look-again also works when the change was made on another tab. *Built differently from the first draft, which kept `lastChangedAt` as state in `GroupDetail` and passed it down as a prop: the cache needs no props, and the bell can reach it.* A confirm or reject in the bell **is** followed by a look-again in the open tab (the first draft said it was not): the creditor sees the booking without waiting for the 10 s poll.
- **Removed:** `lib/paymentAnswered.ts` and its listener in `SettleUpTab`, `announcePaymentAnswered` in `InboxBell`, the bell's hand-written reload calls where invalidation replaces them.
- **Unchanged:** the bell keeps its own 30 s poll (it may become a query with `refetchInterval: 30_000`, builder's choice); `useLookAgain` itself.

### Backend, contract, diagram

No change.

### Docs (same change)

- `04` section 5: "State: local `useState`; server data through `useApi`. No global state library." becomes "server data only through `useApi`, which is TanStack Query; no client state library."
- `04` section 4, the eventual-consistency line: the look-again after an own write plus the 10 s poll of the open group page.
- `RUNNING.md`: nothing (no new setup).
- Requirement J4 step 2 (FAVS folder, `work/ShareWise_Requirements.md`): participants default "only me", one click selects everyone (D1).

## Failure modes (`/edge-case-probe` plan mode, 11.11)

The change is frontend-only, so the risk areas that apply are eventual consistency, concurrency of a poll with a write, and **forms that now receive new props while the user types**. Today the plan and the member list reload only on the user's own actions; with a 10 s poll they change under an open form. Ranked by impact × likelihood.

| # | Case (who, how often) | What happens without care | Stopped by | Mark |
|---|---|---|---|---|
| F1 | **Waiver form switches debtor.** A creditor with two or more debtors has chosen Bob in the "Waive" form (`SettleUpTab.tsx:229`, `WaiveDebtForm`); meanwhile anyone records an expense or a payment is booked, the simplified plan changes and Bob no longer owes this creditor. Any group with three or more people, during a trip. | `WaiveDebtForm.tsx:39` falls back to `waivableEdges[0]` when the chosen debtor is gone and keeps the typed amount: one click waives that amount for **a different person**. The server accepts it (it is within that person's debt). Money goes to the wrong person. | Fix (D2, decided, built): untouched, the form follows the plan; once the user chose a debtor or typed anything it stays about that person; a chosen debtor who is gone gives no selection and the message "Please choose who owes you." After the user's own waiver the debtor stays selected while still in the plan (a partial waiver), else the form follows the plan again. | **tested** |
| F2 | **Expense split silently grows.** A member fills "Add expense" while someone accepts an invitation (a trip that is still being set up). | `ExpenseForm.tsx:38,42` stores who is *excluded*, so a member who appears through the poll is **included**: the shares change before submit, the expense is split over one more person than the user chose. Visible in the share preview, easy to miss. | Fix (D1, decided): the form stores who is **included**, starting with only the recorder (ticked and locked, R18); everybody else starts unticked, a button "Select everyone" ticks all members shown. A member who appears through the poll is therefore unticked like everybody else. | **needs a test** |
| F3 | A poll is in flight when the user's own write invalidates the same query | An old answer could overwrite the new one. | TanStack cancels the running fetch on `invalidateQueries` (default `cancelRefetch: true`). | handled by design |
| F4 | A poll lands between another member's write and the ledger booking (milliseconds, rare) | Balances refetched old. | The next poll, 10 s later. | accepted |
| F5 | "I paid" form open while the debt is settled by someone else | The row (keyed by the other person, `SettleUpTab.tsx:198,213`) disappears and takes the form with it; typed text lost. A smaller payable amount is caught by `PaymentForm`'s check against the current `payableAmount` and by the server (422). | — | accepted |
| F6 | The person who wrote switches tab before the ledger booked (every expense) | Balances shown old, "Updating…" missing. | The change time lives in the query cache (`useGroupChange`) and every tab runs the look-again when it mounts. | **tested** (`GroupDetail.test.tsx`, `afterAnExpenseTheSettleUpTabLooksAgainWhenOpened`) |
| F7 | Network gone for a while (train, laptop sleeps) | Each poll fails. | TanStack keeps the last data and sets `error`; the page shows the banner over the old data (the group page keeps its tab and form, `GroupDetail.test.tsx`); the next successful poll clears it. Two details of the production client: a status-0 or 5xx failure is retried three times with backoff first (about 7 s before the banner shows), and while the browser reports itself offline (`navigator.onLine`) TanStack pauses the queries instead of failing them, so a real outage shows the old data without a banner and refetches on reconnect. We accept that: old data that heals by itself beats an error message. Telling the user they are offline is a separate, visible decision. | handled by design |
| F8 | Sign out, another person signs in on the same computer | Cached data of the first user shown to the second. | Sign-out is a full form post and page navigation (`AuthBar.tsx:19`), so the in-memory cache is gone. | handled by design |
| F9 | Expense from a member the cached group does not know yet | "Former member" for up to one poll (`ExpenseList.tsx:10`), then the polled group detail knows the name. | — | accepted |
| F10 | Session gone while the page polls | First 401: `api()` sends the visitor to sign in (L4). | `fda920f` | handled by design |

**In one line, also checked and fine:** browser tab hidden (polling paused, refetch on focus); the same user in two browser tabs (each polls, harmless); a wrong group id in the URL (404 banner, polling continues every 10 s: accepted, harmless); own write plus poll both refetch (two requests); cached plan for a moment on tab switch (refetch starts at once, server validates); the plan recomputed every 10 s per viewer (cheap at this scale); `e2e/mallorca.spec.ts:77` `networkidle` with a 10 s poll (check in the journey run).

**Parked, with the trigger:** a removed or former member's open page (403 every 10 s) and groups that are closed or archived: when `removeMember`, `leaveGroup`, `closeForSettlement` get endpoints.

### Decisions from the probe

| # | Question | Options | Recommendation |
|---|---|---|---|
| D1 | A member joins while "Add expense" is open: is the new member in the split? | (a) everybody ticked when the form opens (J4 as written), a member who joins meanwhile unticked; (b) **nobody but the recorder ticked by default, plus a "Select everyone" button**; then untick who is not part of it | **(b), decided by the user 11.11**: a split only ever contains people the user ticked, so a joining member can never slip in; "Select everyone" keeps the common case (whole group) one click. Changes requirement J4 step 2 ("participants (default: everyone)" becomes "default: only me, one click selects everyone"); `work/ShareWise_Requirements.md` lives in the FAVS folder, outside this repository, and is updated there in the same step. |
| D2 | The chosen debtor disappears from an open "Waive" form | (a) no selection, message "Please choose who owes you."; (b) silently the first debtor (today) | **(a), decided by the user 11.11**: (b) can waive money for the wrong person. |

## Left to the builder

The `Providers` file name; whether the bell becomes a query; how `useApi` takes the poll option; how tests get a fresh `QueryClient` per test.

*Built (11.11):* `components/QueryProvider.tsx`; the bell stays a hand-written poll (30 s) and only gains `useQueryClient` to mark a group changed; `useApi(path, { poll: true })`; tests render through `renderWithQueryClient` (`lib/renderWithQueryClient.tsx`, a client without retries per test). `package-lock.json` was edited by hand, because npm on Windows drops the other platforms' entries (`rolldown`, `lightningcss`, `fsevents`).

## Test plan (one rule, one level)

- **Component tests** need a `QueryClient` wrapper with `retry: false` (otherwise the error-banner tests wait for TanStack's retries, `Balances.test.tsx:49-60`); one helper used by every test that renders `useApi`.
- **Tests to rewrite:** `InboxBell.test.tsx` (`PAYMENT_ANSWERED` at lines 6, 202-229), `SettleUpTab.test.tsx` (lines 7, 156), `Balances.test.tsx` (`lastRecordedAt` at lines 82, 95: becomes `lastChangedAt`), `GroupDetail.test.tsx:97` (`tellsTheUserThatBalancesFollowAfterAnExpenseWasRecorded`: must still pass, now also for Settle up). `useLookAgain.test.tsx` stays.
- **New component tests:** `WaiveDebtForm`: the chosen debtor disappears from the plan, the form shows no selection and does not submit (F1); `ExpenseForm`: only the recorder is ticked when the form opens, "Select everyone" ticks all, a member added to `members` while the form is open is unticked and not in the shares (F2, D1; `Expenses.test.tsx:41` expects everyone ticked today and changes); an own write invalidates the group's queries; the group page refetches after 10 s and not while the document is hidden (fake timers); the bell's confirm invalidates the payment's group.
- **Journey (Playwright):** two browsers; Alice confirms Bob's payment, Bob's open Settle up and Balances tabs update without a reload (assertions retry up to 15 s). Check `e2e/mallorca.spec.ts:77` (`waitForLoadState("networkidle")`) still settles with a 10 s poll. The helper `addExpense` (`e2e/mallorca.spec.ts:39`) relies on everyone being ticked by default: it clicks "Select everyone" before saving (D1).
