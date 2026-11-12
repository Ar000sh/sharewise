# Prompt: three design directions for ShareWise

Copy everything below the line into a new Claude session (with the `impeccable` skill installed) and attach or point to `docs/ui/UI_REQUIREMENTS.md`.

---

Create one shareable Artifact (Design canvas) with **three clearly different design directions** for ShareWise, a group-expense app (Splitwise-like) for friends who share costs on trips or in flats.

**Read first:** `docs/ui/UI_REQUIREMENTS.md`. It is the brief. Every feature in it must be reachable in every direction. Do not invent features it does not list; if you think one is missing, say so at the end instead of adding it silently.

**Use the `impeccable` skill.** This is an app (task-focused, the "Operate" mode), not a landing page. Start with `shape` to settle the product context and the three directions, build each direction, then run `critique` and `polish` on each before publishing. Do not use any hook, live mode or network setup beyond what the skill needs.

**Three directions.** Each one needs its own point of view: its own type, colour, layout logic and navigation pattern. They must differ in structure, not only in colour. Name each in one line and say who it suits.

**Screens, for every direction (one board each, nothing combined):**
My groups · Create group · Inbox · Expenses (list) · Add expense · Balances · Settle up · Members and invitations · Group status · Activity · Payment dialogs (report, reject with reason, waive) · Sign in and invitation entry · Group states (closed, archived) · Loading, error and empty states.

**Rules for all boards**
- Light and dark, both designed. Phone and desktop, both designed.
- Use the Mallorca 2027 data from section 5 of the requirements. Do not change the numbers; they are verified.
- Settle up must show what needs the current user first, and must handle the creditor side (answer, waive) and the debtor side (report a payment) clearly. Show the people-specific views, not one merged "preview".
- Money format, wording and accessibility follow section 4 of the requirements: sentence case, domain words, contrast 4.5:1, 44 px touch targets, nothing shown by colour alone.
- Real buttons, labels and states. No emoji. No placeholder text like "Lorem ipsum".
- Label the boards so a team can comment on them by name (for example `A · Add expense`).

**Finish by** telling me: which features of the requirements each direction covers, anything you could not fit, anything you think the requirements miss, and which direction you would pick and why.
