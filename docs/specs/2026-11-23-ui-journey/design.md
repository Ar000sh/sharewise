# UI redesign "Journey" (mockup direction B): design

Date: 2026-11-23. Branch `feature/ui-journey-design` (from `main` at `dccfdc7`). Status: draft; the user's decisions U1 to U4 are taken, the rest follows from them or is marked (call). Next: `/edge-case-probe` plan mode, `/design-review` (it touches money on screen and the balance read model), then a plan per branch.

## Why

The app works end to end (sign-in, groups, expenses, balances, settle up, inbox), but it looks like a Tailwind skeleton: black on white, one font, inline forms everywhere. The team compared three design directions and chose **B "Journey"**. This document says how the existing frontend becomes B without losing behaviour, which two small API additions B needs, and in which order the work lands.

**What stays unchanged:** the data flow (route handlers + `proxyToBackend`, `useApi` on TanStack Query, the 10 s poll of the open group page, `useLookAgain`), the money rules (`lib/money.ts`), the error table (`messageFor`), the session handling. This is a change of presentation and of where things sit on screen, not of how the app talks to the server.

## Source of the design

- Boards: branch `mockups`, folder `docs/ui/mockups/round-2-impeccable/`, files `B-01-groups.dc.html` to `B-15-states.dc.html` (desktop and phone, light and dark each). Colour, type and component styles: `b.css` in the same folder (with `base.css` for the icons). Canvas: https://claude.ai/artifact/7t1Qb3Ki4888xYmFSMV72R (needs access to the team's account).
- Brief: `docs/ui/UI_REQUIREMENTS.md` on the same branch (features F1 to F11, "Built" vs "Planned").
- The branch is **referenced, not merged** (U4). Nothing in the code imports from it; this document names the boards by file name.

B in one line (from the round-2 README): *top bar, one centred feed, big money, an Add expense button that stays in reach.*

## Decisions

| # | Decision | Why | Status |
|---|---|---|---|
| U1 | Direction **B "Journey"** of round 2. | Team choice. | user, 23.11 |
| U2 | The backend delivers **the caller's balance per group** (A2) and **paid / share / settled per member** (A1). The **join date** of a member is left out for now. | Both numbers are central to B (the home list leads with the own balance; the Balances board explains each balance). The join date is decoration; `Membership.joinedAt` exists in the diagram, so it can follow cheaply later. | user, 23.11 |
| U3 | **Planned features are hidden completely**, not shown disabled: the Activity tab, the Status tab and its admin actions, the invite link, remove member / leave group, cancel invitation, the list of open invitations, former members, the summary across groups, the invitation entry page (invite link). | The requirements say "design for it; do not assume it works". Disabled tabs and "Planned" badges look unfinished in the presentation (P3 15.12). Each comes back with its feature. | user, 23.11 |
| U4 | The mockup sources stay on branch `mockups`; this repo's `main` only mentions them. | The user's choice; they are reference material, not code. | user, 23.11 |
| U5 | The inbox becomes **its own page `/inbox`**, reached from the top bar ("Inbox" with a count). The bell dropdown goes. **Supersedes N6** of `docs/specs/2026-11-03-notifications/design.md` ("bell with a dropdown, option A of the mockup"). | B-03 shows the inbox as a page; on a phone a dropdown with confirm/reject forms is cramped. The aggregating handler `/api/inbox` and its 30 s poll for the count stay. | follows from U1 |
| U6 | **Report a payment, reject with reason and waive a debt open as a dialog** (native `<dialog>`, a bottom sheet at phone width) instead of inline forms. | B-12. A dialog keeps the plan readable and gives each action one focused place; native `<dialog>` brings focus trapping and Escape for free (no new dependency). | follows from U1 |
| U7 | **Add expense is its own page** `/groups/[id]/expenses/new`, opened from the "Add expense" button in the group header and, at phone width, a floating button. After saving it returns to the Expenses tab. | B-04 / B-05: the most frequent action is always one tap away and gets the full screen on a phone. | follows from U1, (call) for the route name |
| U8 | **Theme tokens** are the CSS variables of `b.css`, defined once in `globals.css` and switched by `prefers-color-scheme` (not by a class as on the boards). The existing names `background` and `foreground` keep their meaning; the others are added (table below). | One place for colour; existing classes keep working; dark mode keeps following the browser (`04` section 5). | (call) for the token names |
| U9 | **Fonts:** Bricolage Grotesque for headings and big money, DM Sans for text, through `next/font/google` (self-hosted at build time, no request to Google from the browser). Geist goes. | B's type. `next/font` is how the layout loads Geist today. | follows from U1 |
| U10 | **Glossary before mockup wording:** the tab stays "Members" (B says "People"); a balance row says "paid … · share …" (B says "owes …", which collides with "owes" for a negative balance); amounts are labelled "Amount (EUR)" as on the boards. | `04` section 5: UI text uses `docs/domain/Ubiquitous_Language.md`. | (call) |
| U12 | **A1 is its own read query**, not three more fields on `MemberBalance`; the reversal shape is noted in `diagram-deltas.md` (details under A1). | Design review 23.11 (below): production reads balances through `GroupLedgerRepository.balances`, not `GroupLedger.projectBalances()`, so the first draft's invariant test would have checked a copy nobody serves; and `MemberBalance` is built at 25 places (plan, open debt, zero-balance detection) that never need the breakdown. The API answer is the same. | user, 23.11 |
| U11 | **Parked:** B-14's banner for a group that is closed for settlement or archived. | No operation sets either status yet (`closeForSettlement` and `archiveGroup` have no endpoint; `AllBalancesSettled` has no consumer), so the banner would show a state nobody can reach (`/edge-case-probe` proportion rule). **Trigger:** the branch that builds close, reopen or archive also builds the banner from the existing `status` field. *(The first draft built it; dropped in the plan-mode probe, 23.11.)* | parked |

## Screens: board → code → data

| Board | Route / component | Data | API |
|---|---|---|---|
| B-01 My groups | `app/page.tsx`, `GroupList` | `/groups`; own balance per group from `/me/balances` (A2); "n waiting for you" per group from the one inbox query (F3) | **A2 new** |
| B-02 Create group | `app/groups/new/page.tsx`, `CreateGroupForm` | unchanged | built |
| B-03 Inbox | **new** `app/inbox/page.tsx`, `InboxBell` becomes `Inbox` (page) + `InboxLink` (top-bar count) | `/api/inbox` (unchanged) | built |
| B-04 Expenses | `GroupDetail` (header, hero, pill tabs), `ExpenseList` (grouped by day, "your part" per row) | `/groups/{id}`, `/expenses`, own balance from `/balances`, waiting count from the inbox query (F3) | built |
| B-05 Add expense | **new** route (U7), `ExpenseForm` | unchanged | built |
| B-06 Balances | `BalancesTab` | `/balances` with A1 fields | **A1 new** |
| B-07 / B-08 Settle up (creditor / debtor) | `SettleUpTab`: "Needs your answer" first, then "You can receive", "You owe", "Between other members", then **payment history with status filter** | `/settlement-plan`, the inbox query's waiting payments (filtered to the group, F3), `GET /groups/{id}/settlement-payments` (exists, not shown today) | built |
| B-09 Members | `MemberList` (balance per row from `/balances`), `InviteForm` (role as a segmented choice) | without join date (U2), without open invitations and former members (U3, no endpoint) | built |
| B-10 Status, B-11 Activity | — | hidden (U3) | planned |
| B-12 Dialogs | `PaymentForm`, reject form, `WaiveDebtForm` in a shared `Dialog` component (U6) | unchanged | built |
| B-13 Sign in | `app/login/page.tsx`: the first state only (sign in / create account); the invitation-link states are hidden (U3) | — | built |
| B-14 Group states | — | parked (U11): no operation produces the states | planned |
| B-15 Loading, error, empty | `ErrorBanner` gains a "Try again" button (calls `reload`); empty states per list with B's wording | — | built |

**Derived in the frontend, no backend change:** the waiting count per group (the waiting payments in the inbox query carry `groupId`; never a second query on `/me/settlement-payments`, whose own cache entry no invalidation would reach, F3); "your part" of an expense (paid minus own share, from `shares`, as an integer-cent helper **inside `lib/money.ts`**, the place `04` section 3 allows share arithmetic); the payment history.

## API additions (contract first, `/contract-change`; backend module `settlement`)

**A1. `Balance` gains `totalPaid`, `totalOwedShare`, `totalSettled`** (decimal strings like every `Amount`).
- `totalPaid`: sum of the member's `EXPENSE_PAID` entries; `totalOwedShare`: sum of their `EXPENSE_OWED` entries, as a positive amount; `totalSettled`: sum of `PAYMENT_SENT`, `PAYMENT_RECEIVED` and `DEBT_WAIVED` (signed).
- Why three and not two: after a confirmed payment, paid minus share is no longer the balance. With `totalSettled` the screen shows every part from the server and does no balance arithmetic of its own (the anti-goal of the live-group-views design).
- **How it is built (U12):** its own repository query sums the entries per member **and entry type** (`group by userId, entryType`); one method on `LedgerEntryType` says which of the three sums a type belongs to; the breakdown is folded in the `Balances` read path and `netBalance` is the sum of the three, so `netBalance = totalPaid − totalOwedShare + totalSettled` holds **by construction**. `MemberBalance(userId, netBalance)` and everything that reads it (plan, open debt, zero-balance detection) stay as they are. Tested once, by an integration test of the real query over all five entry types.
- Names follow the contract (`totalAmount`, `owedShare`). The breakdown is a read model of its own (proposed `BalanceBreakdown`), recorded in `docs/diagram-deltas.md` next to `GroupBalance` (A2); the diagram's `MemberBalance` is unchanged.
- **Reversals:** the diagram has one `EXPENSE_REVERSAL` type. A1 needs a reversal to count into `totalPaid` or `totalOwedShare` (never `totalSettled`), and one type cannot say which, because a payer who is also a participant gets both for the same expense. The `feature/balance-breakdown` branch adds one line to `docs/diagram-deltas.md`: when corrections and removals are built, the reversal is split into a paid and an owed variant (or booked as negated `EXPENSE_*` entries with the correction as `referenceId`; the unique constraint `(reference_id, user_id, entry_type)` of `V401` rules out the expense id). Nothing changes now.

**A2. `GET /me/balances`**: `[{ groupId, netBalance }]`, the caller's balance in every group where the ledger has entries for them. A group without entries is absent and shown as 0,00 €.
- Why not a field on `Group` (`GET /groups`): `group` would have to call `settlement`, and `settlement` already depends on `group::api`: the cycle that `docs/conventions/01` section 5.1 forbids. The frontend asks both and joins them on `groupId`, as two `useApi` calls in `GroupList` (an aggregating handler like `/api/inbox` would add a merge with partial-failure handling for a list of a handful of groups; design review 23.11).
- Name and place in the diagram: a read model next to `MemberBalance` (proposed `GroupBalance`); the diagram or `docs/diagram-deltas.md` records it.

## Theme

Tokens from `b.css` (light / dark), as Tailwind theme colours in `globals.css`:

| Token (Tailwind) | `b.css` | Light | Dark | Use |
|---|---|---|---|---|
| `background` | `--bg` | `#FFF9EF` | `#0C1B2A` | page |
| `foreground` | `--ink` | `#11283F` | `#F6EFE2` | text, strong buttons, active pill |
| `surface` | `--sf` | `#FFFFFF` | `#132A40` | panels, inputs, dialogs |
| `surface-2` | `--sf2` | `#FDEFD5` | `#0A1522` | hero, notes, current top-bar item |
| `muted` | `--mut` | `#4F6174` | `#AAB9C9` | quiet text |
| `line` | `--line` | `#EBDDC2` | `#254159` | borders, dividers |
| `accent` / `on-accent` | `--acc` / `--onacc` | `#FFC83D` / `#11283F` | same | primary button, counts |
| `positive` / `negative` | `--pos` / `--neg` | `#0A6E69` / `#B42318` | `#5ED6CF` / `#FF9D8F` | money direction, danger button |
| `warning` / `on-warning` | `--warn` / `--warnt` | `#FFE3B8` / `#5B3200` | `#4A3410` / `#FFD99A` | "waiting for you", refusals |
| `focus` | `--foc` | `#1D4ED8` | `#8FB1FF` | focus ring (3 px, offset 3 px) |
| `person-1` … `person-5` | `--p1` … `--p5` | pastels | dark pastels | avatar backgrounds |

- Every token changes with the mode, so the dark-mode traps of `04` section 5 (fixed background without its own text colour) do not arise when only tokens are used. `src/lib/themeRules.test.ts` keeps forbidding the fixed palette; its list of text colours accepts the new tokens. `04` section 5 is rewritten for the tokens **in the same commit as the theme** (`CLAUDE.md`: docs change with the code).
- Avatar colour: a stable function of `userId` → `person-1..5` (the same person has the same colour everywhere, requirement "Identity").
- Accessibility from B: touch targets at least 44 px (buttons 48 px), money is never shown by colour alone (sign plus "is owed" / "owes"), focus ring on every control, contrast 4.5:1 (B's pairs; checked once in both modes in the theme branch).

## Order of work (one branch and pull request each)

1. `feature/ui-theme`: tokens, fonts, the top bar (logo, "My groups", "Inbox" with count, avatar, sign out), base pieces (button variants, badge, avatar, note, panel, `Dialog`), `ErrorBanner` with "Try again". After this, every screen already looks like B. *Built (23.11) with less: the base pieces are the utilities `btn-primary`, `btn-secondary`, `card` and the `Avatar`; the badge, note and panel are plain token classes until a third use; `Dialog` moves to `feature/ui-settle-up`, its first user; the "My groups" / "Inbox" links come with the Inbox page (`feature/ui-members-inbox`), until then the logo leads home and the bell stays (keep it simple: no piece without a user).*
2. `feature/ui-groups`: My groups (without the balance until A2 is merged), Create group, Sign in.
3. `feature/ui-group-page`: header, hero (own balance, waiting count, "Updating…"), pill tabs, Expenses by day, Add expense page and floating button. *Built (23.11) without the waiting count, which needs the one inbox query (F3) and comes with `feature/ui-members-inbox`; the share preview of the Add expense form stays a list below the checkboxes instead of B's amount per row (call: keeps the tested preview).*
4. `feature/ui-settle-up`: Settle up in B's order, payment history, the three dialogs.
5. `feature/ui-members-inbox`: Members tab, Inbox page (removes the bell).
6. `feature/balance-breakdown` (backend + contract, A1 and A2) — can run in parallel to 2 to 5; then a small frontend branch fills the Balances rows and the home balance.

Each frontend branch: both colour modes and phone width (390 px) looked at, `npm run lint && npm run typecheck && npm test && npm run build`, and `npm run e2e` against the running stack.

## Tests and docs that change with it

- **Playwright (`e2e/mallorca.spec.ts`)** finds things by label and role, so wording changes break it on purpose; each branch updates the spec in the same commit: "Amount (€)" → "Amount (EUR)"; the add-expense button becomes a link to the new page and the form's button "Save expense"; "Invite" → "Send invitation"; the Inbox button → link to `/inbox`; "Create your first group" → B's empty state ("Create group"); waive and report move into dialogs.
- **Component tests** move with their component (`InboxBell.test.tsx` → `Inbox.test.tsx`); new ones for the day grouping, the "your part" helper (in `money.test.ts`), the dialogs (open, submit, Escape), the hero.
- **Docs:** `04` section 5 (tokens, theme branch); `RUNNING.md` Mallorca walkthrough (bell → Inbox page, "Waive manually" form → dialog, "Amount (€)"); a note under N6 in the notifications design that U5 replaced it; `docs/diagram-deltas.md` or the diagram for A1 and A2.

## Non-goals (parked, with the trigger)

- Everything in U3, and the closed/archived banner (U11): with the feature that makes it real.
- Keycloak's own sign-in and registration pages keep Keycloak's look (keycloak-auth design, D1). Trigger: if the presentation needs one look end to end, a Keycloak theme is its own small project.
- The join date in Members (U2): when someone wants it; `joinedAt` exists in the domain.

## Failure modes (`/edge-case-probe` plan mode, 23.11)

The change is mostly presentation, so the risk areas that apply are **forms that move into dialogs and pages while the 10 s poll keeps running**, **counts and balances that now show in more places** (eventual consistency), and **money shown in new ways**. The F-numbers of the live-group-views design are referred to as LGV-F1 etc. Ranked by impact × likelihood.

| # | Case (who, how often) | What happens without care | Stopped by | Mark |
|---|---|---|---|---|
| F1 | **Waive dialog while the plan changes.** A creditor has the waive dialog for bob open; meanwhile a payment of bob is confirmed or someone records an expense, and the plan changes. Any active group. | A dialog that kept the edge it was opened with offers the old amount; a dialog built on today's `WaiveDebtForm` with several edges could fall back to another debtor (LGV-F1): money waived for the wrong person. | The dialog is opened **for one debtor** (from that row, as B-12 shows: "Debtor bob", no picker) and reads that debtor's edge from the current, polled plan; when the edge is gone it says "Nothing is open with bob any more." and offers no submit. The amount check against `payableAmount` and the server's 422 stay. The separate "Waive manually" picker goes (B has none), which removes the LGV-F1 risk instead of guarding it. | **needs a test** |
| F2 | **Report-a-payment dialog while the debt shrinks** (the creditor waives part, or another payment is confirmed). | The old amount is offered; a submit above the new `payableAmount` is refused by the server (422). | Same shape as F1: the dialog reads the creditor's current edge; the client check uses the current `payableAmount` (as `PaymentForm` does today). | handled by design (with F1's test pattern) |
| F3 | **Answering a payment in one place leaves the count stale elsewhere.** Alice confirms in the Settle up tab ("Needs your answer", new in B) or on the Inbox page. Every payment. | Today the bell keeps the inbox in its own state; with two places to answer, the top-bar count or the other list shows the payment as still waiting until its next 30 s poll. | The inbox becomes **one query** (`useApi("/inbox", …)`, 30 s poll) read by the top-bar count, the Inbox page, the per-group waiting counts and the Settle up section; **one function** answers a payment and invalidates both the inbox query and the group (`markGroupChanged`). | **needs a test** |
| F4 | **Look-again after saving on the Add expense page.** Every expense: the form now lives on its own page (U7) and navigates back to the group. | The Expenses / Balances tab or the hero would show the old balance without "Updating…". | `markGroupChanged` stores the change time in the query cache, which survives a client-side navigation (`router.push`); the hero and each tab run `useLookAgain` on mount (LGV-F6). | **needs a test** (save on the page → group page shows "Updating…") |
| F5 | **"Your part" of an expense computed wrongly.** Every expense row (new in B). | Recomputing a share (`total / n`) gets remainder cents wrong (R13: the remainder goes to the first participants), so a row says −15,10 € where the server booked −15,11 €. | "Your part" = (`totalAmount` if I paid) − my `owedShare` from the server's `shares`, never re-split; one integer-cent helper in `lib/money.ts`. (A removed expense cannot exist yet: no remove operation; its "0, not counted" comes with that feature.) | **needs a test** (`money.test.ts`: remainder cents, payer who is also a participant, payer who is not) |
| F6 | **A1's three numbers do not add up** (`netBalance ≠ totalPaid − totalOwedShare + totalSettled`). Every balance row once A1 exists. | The Balances row would contradict itself. | One query sums per entry type; each type maps to exactly one of the three sums (`LedgerEntryType`); `netBalance` is their sum (U12), so the four cannot disagree. | **needs a test** (integration test of the query over all five entry types, against Postgres) |
| F7 | **Day headings off by one.** Expense `date` is a plain date (`2027-07-19`); `new Date("2027-07-19")` is UTC midnight and shows 18 Jul in a time zone west of UTC. Rare for this team, but a demo machine set to UTC−x would show it. | Expenses grouped under the wrong day. | The date is formatted from its parts (or with `timeZone: "UTC"`), in one helper. | handled by design |
| F8 | Typed input lost when the row under an open dialog disappears (the debt was settled by someone else). | The dialog closes with the row, the note is gone (LGV-F5). | F1 keeps the dialog open with an explanation instead. | handled by design (F1) |
| F9 | Waiting counts and the home balance lag (top-bar count polled every 30 s; the home list not polled; A2 is booked by the ledger a moment after a write). | A count or a home balance is up to 30 s old, or old until the page is opened again or gets focus. | Refetch on mount and focus (TanStack defaults); the group page itself is polled every 10 s. | accepted |
| F10 | The same payment answered twice (Inbox page in one tab, Settle up in another). | Second answer. | The server answers 409 `PAYMENT_NOT_PENDING`; `messageFor` shows it; F3's invalidation removes the row. | handled by design |

**In one line, also checked and fine:** a member who joins while the Add expense page is open stays unticked (LGV-F2, `ExpenseForm` stores who is included); Escape or a click outside closes a dialog only when it is not sending (the submit is disabled while busy, as today); the hero adds one `/balances` query to every tab of the group page (one more small GET per 10 s, negligible); A2 lists ledger entries of a group the user can no longer see: the frontend joins on `/groups`, so it is never shown (and nobody can leave a group yet); the font files are fetched at build time by `next/font`, so the CI image build needs network, as it already does for Geist.

**Parked, with the trigger:** the closed/archived banner (U11, when close/archive is built); a former member's balance row (when leave/remove is built: `Balances.of` lists current members only).

## Design review (23.11, independent Opus reviewer, read-only)

**Diagnosis:** the redesign is mostly presentation and its complexity is inherent: A1 must come from the server (the expense list and the ledger are only eventually consistent, so a breakdown built in the browser would disagree with `netBalance`); A2 is a per-user query inside `settlement` and avoids the `group → settlement` cycle; the one-debtor dialogs remove the LGV-F1 risk instead of guarding it. The accidental complexity sat in how A1 was placed. Verified in the code before acting on it:

- **Two definitions of a balance; the draft tested the unused one.** Every production reader (`BalancesImpl`, `OpenDebtPolicy`, `GroupLedgerService`) goes through the query `GroupLedgerRepository.totals`/`balances`; `GroupLedger.projectBalances()` loads all entries and is not used by the app (its own Javadoc). → U12.
- **`MemberBalance` widened for one reader.** 25 constructor calls; only `BalanceController` needs the breakdown; the `Balances` port has no consumer outside `settlement`. → U12.
- **The single `EXPENSE_REVERSAL` type would carry two meanings under A1.** → the note under A1.
- **Smaller, fixed in this document:** the waiting count read from `/me/settlement-payments` as well as from the inbox query would have recreated F3 (two cache entries) → everything reads the inbox query; F5 guarded a removed expense that no operation can produce → dropped; A2's open choice → two `useApi` calls.

**Options offered:** (1) A1 as its own query, (2) the same plus the reversal note, (3) drop A1 and show only the net balance. **Decision: (2)**, user 23.11. Nothing needs a migration, a new event, a new module dependency or a change in `group`; at the service split A2 stays a plain per-user query of the settlement service.

## Open points

- **Who builds A1 and A2** (backend, contract, diagram deltas)? Until then the frontend shows the net balance only and leaves the home balance out.

## Left to the builder

The file names of the new base components; the exact Tailwind class strings; whether the floating button hides while the keyboard is open on a phone.
