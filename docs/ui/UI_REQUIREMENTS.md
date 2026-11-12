# ShareWise UI requirements

What the interface must let people do, independent of how it looks. Use this as the brief for any design or mockup tool: every feature below must be reachable, and nothing here prescribes layout, style, colour or navigation pattern.

Sources: the user journeys and business rules (`work/Ex1b_ShareWise_User_Journeys.md`, rules R1 to R18 / L1 to L6), the HTTP contract (`docs/api/openapi.yaml`) and the current frontend. Names follow the domain diagram and glossary (group, expense, paid by, split, balance, settlement payment).

## 1. Overview

ShareWise is a shared-expenses app: friends form a **group** (a trip, a flat), record who paid what, see who owes whom, and settle up with a small number of payments. Everything is in **EUR**. There are no passwords in ShareWise: sign-in is done on an external identity provider.

The product in one sentence: *record expenses together, always know the balance, settle with the fewest payments.*

### People and roles
| Role | What they can do |
|---|---|
| Visitor | Opens an invitation link, must sign in or register first |
| User | Signed in; creates groups, sees their own groups and inbox |
| Member | Records expenses, reports payments, confirms payments owed to them |
| Admin | Everything a member can, plus invite, promote, close/reopen, archive, remove members. The creator is the first admin |

### Group lifecycle (the UI must make the current state obvious)
`OPEN` → `CLOSED_FOR_SETTLEMENT` → `ARCHIVED`
- **Open:** everything works.
- **Closed for settlement:** expenses are frozen (no add, correct or remove); payments still work; an admin can reopen.
- **Archived:** final and read-only. Happens automatically when the last balance reaches 0, or by an admin when all balances are 0.

### Status legend used below
- **Built:** exists in the API and the app today.
- **Planned:** in the requirements and shown in the mockups, but not in the API yet. Design for it; do not assume it works.

## 2. Features

### F1. Sign in and account — Built
- Sign in, create an account (both leave the app for the identity provider and return).
- Show who is signed in; sign out.
- Any request that finds the visitor signed out sends them to sign in and back to where they were.

### F2. My groups (home) — Built
- List every group the user belongs to: name, status, number of members, the user's own balance in it (owed / owes / settled), and the latest activity.
- Create a group (name; currency is EUR, status starts open, creator becomes admin).
- Open a group.
- Empty state for a user with no groups yet.
- Summary across groups (total owed to me, total I owe, things waiting for me): *Planned*, optional.

### F3. Inbox — Built
One place for everything that needs the user or tells them something:
- **Invitations** to join a group: accept or decline.
- **Payments waiting for my answer** (someone says they paid me): confirm or reject.
- **Notifications** about what happened (member joined, invitation declined, member promoted, payment confirmed or rejected, debt waived, expense recorded): read one, mark all as read.
- A count of unread / waiting items visible from anywhere in the app.

### F4. Invitations and joining — Built
- An admin invites a person (username or email) and chooses whether they join as member or admin.
- Open invitations are listed with who, role, and when they expire (7 days); an admin can cancel one (*Planned*).
- The invited person sees the group name, status, member count, the role they would get, and the expiry, and can join or decline.
- Expired and already-used invitations show a clear explanation and a way back.
- Share an invite link: *Planned* (shown in mockups).
- A late joiner only shares in expenses recorded after joining (R10); say so before they join.

### F5. Group overview
Every group page shows: name, status, currency, number of members, whether the numbers are up to date, and the sections below. The user can always see their own balance and what is waiting for them in this group.

### F6. Expenses
**Record an expense — Built (equal split only)**
- Fields: title, amount, date, paid by (defaults to the current user), who shares the cost.
- Split equally between the selected members; show each person's share before saving.
- The recorder always stays a participant (see `docs/2026-11-09-recorder-stays-participant-spec.md`).
- Amount rules: greater than 0, at most two decimals, comma or point accepted; errors shown next to the field.
- After saving: confirmation, and the balances are marked as updating (they are eventually consistent).

**List expenses — Built**
- Newest first. Each row: date, title, paid by, how it is split, amount, and the current user's part (plus or minus).
- Status markers: corrected, removed (removed ones stay visible but do not count).
- A member who joined later is not in earlier expenses; a former member is still named ("left").

**Planned**
- Exact and percentage splits (shares must add up exactly to the amount; percentages to 100 %; remainder cents go to the first participants).
- Correct an expense (recorder, payer or admin): keeps the old version in a visible history with the reason; participants are notified.
- Remove an expense (soft delete, same people may do it).
- Expense detail: shares per person and version history.
- Quick entry (type a sentence) as an alternative to the form: optional.

### F7. Balances — Built
- One row per current member: paid, owed, and net balance (positive = is owed, negative = owes).
- The sum of all balances is 0; show it.
- Explain how a balance is made and that only confirmed payments change it.
- Show that the numbers are refreshing right after an expense was recorded.

### F8. Settle up — Built
- **Settlement plan:** who should pay whom, with the fewest payments. Separate what is owed to me, what I owe, and what concerns others. State that the plan never changes balances.
- **Report a payment** (I am the debtor): to whom, how much (not more than I owe), a note. It stays pending until the creditor answers.
- **Answer a payment** (I am the creditor): confirm, or reject with a reason. Show who reported it, when, and the note.
- **Payment history** for the group with its status: pending, confirmed, rejected, waived.
- **Waive a debt** (creditor): pick the debtor, amount up to what is open, optional reason. Takes effect at once. Not possible while a payment from that person is waiting for an answer.
- **What needs me comes first:** the user sees what they must answer, pay or can receive before the rest of the plan.
- **Settled state:** when nothing is open, say so clearly.
- **Payment reminder** (creditor nudges a debtor): *Planned*, optional.

### F9. Members — Built (promote, invite); rest Planned
- List members with role (admin or member), join date, and their balance.
- Admin can promote a member to admin — Built.
- Leave the group and remove a member (admin) — *Planned*; only allowed at balance 0. Refusals explain why and link to settle up. The last admin must promote someone before leaving.
- Former members listed separately and still shown in old expenses.

### F10. Group status — Planned
- Admin: close for settlement (freezes expenses and fixes the final plan), reopen, archive (only when every balance is 0).
- Each action says what it will do and why it may be unavailable.
- Closed and archived groups show a persistent, unmistakable explanation of what is no longer possible.

### F11. Activity — Planned (notifications exist today)
- A chronological history of the group, grouped by day: expenses recorded, corrected and removed, members joined, payments reported and answered, status changes.

## 3. States every screen must handle
- **Loading:** shown as "Loading…" style feedback; never a blank screen.
- **Error:** a visible message built from the error code (the app has one message table); never blank. Includes "backend unreachable" and "network error".
- **Empty:** no groups, no expenses, no open invitations, no payments waiting, no notifications.
- **Forbidden / refused:** not an admin, group not open, balance not zero, invitation expired. Say what happened and what to do next.
- **Read-only:** closed and archived groups; disabled actions explain themselves.
- **Eventually consistent data:** balances may lag a moment behind a new expense.

## 4. Cross-cutting requirements
- **Money:** always EUR, always formatted by one rule, two decimals, signed where a direction matters. Positive and negative must be distinguishable without relying on colour alone.
- **Responsive:** every screen works from phone width to wide desktop; the most frequent action (add an expense) is quick on a phone.
- **Light and dark:** the app follows the browser's light or dark setting; both must be designed (`docs/conventions/04_FRONTEND.md`, section 5).
- **Accessibility:** keyboard operable, visible focus, labelled form fields, text contrast of at least 4.5:1, touch targets of at least 44 px, no information by colour alone.
- **Language:** English, sentence case, domain words ("group", "expense", "paid by", "split equally between", "balance"); not "payer", "bill" or "user" for a participant.
- **Security as seen by the UI:** the page never shows tokens; actions the user may not do are hidden or disabled, but the server decides.
- **Identity:** people appear with a display name and a recognisable avatar or initial, consistently across lists, balances and activity.

## 5. Reference data for mockups
The "Mallorca 2027" example (verified, balances sum to 0): alice paid 1.150,00 € and is owed 573,00 €; bob owes 202,00 €; carol owes 72,00 €; david owes 437,00 €; eve is owed 138,00 €. Settlement plan: david pays alice 437,00 €, bob pays alice 136,00 €, bob pays eve 66,00 €, carol pays eve 72,00 €. Other groups for the home screen: "Ski weekend" (closed for settlement, the user owes 48,50 €) and "Flat 12" (archived, all settled).

## 6. Out of scope this semester
Multiple currencies, real money transfer, receipt scanning, several payers per expense, offline mode, recurring expenses, categories and statistics, comments, export (a disabled "coming later" action is fine).
