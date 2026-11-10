# Spec: the person who records an expense cannot remove themselves from its participants

**Date:** 09.11.26 · **Type:** fix (a missing rule) · **Branch:** `fix/recorder-stays-participant` (from `main` at `fdb5ff2`) · **Status:** implemented on this branch (decisions D1 to D3 below were taken on 10.11.26) · **Reported by:** the user, found while using the expense form

This file is the hand-off to the session that implements it. Facts marked *(verified on main)* were read from the code on `main`; everything else is a decision or an open question.

## 1. The problem
In the "Add expense" form the list "Split equally between" shows every member with a checkbox, and **the current user can untick their own box**. The expense is then recorded with the recorder not taking part in it. The user wants that to be impossible: *"as an expense recorder I should not be able to disable myself from the list."*

## 2. What happens today *(verified on main)*
| Where | Behaviour |
|---|---|
| `frontend/src/components/ExpenseForm.tsx` lines 38, 46-48, 112-124 | `excluded` holds the unticked members; `toggle` works for every member, the current user included. The only check is "at least one participant" (line 55). |
| `backend/.../expense/domain/Expense.java` `record(...)` lines 102-134 | Checks R5 (group open), R2 (payer and recorder are active members), R3 (at least one participant, distinct, all active members), R1 (amount positive), R4 (shares add up). **No relation between `recordedBy` and the participants.** |
| Requirements `R3` (`work/ShareWise_Requirements.md`, FAVS folder) | "The payer may or may not be a participant." The open-points list asks "Is the payer automatically a participant?", current answer: **no**. |
| Glossary | **Recorder** = the member who recorded an expense. It can differ from the payer (the form has a separate "Paid by" field). |

## 3. Wanted behaviour (decided by the user)
**The signed-in user who records an expense is always one of its participants.** In the form their own checkbox is ticked and cannot be changed. The other members can still be ticked or unticked as before.

## 4. Decisions (asked before building)
Per the project rule "who decides what": these change what users can do or the shape of the HTTP interface. **Decided:** D1 (a) the recorder only, D2 (b) UI and domain, D3 `R18`. The table keeps the options and the reasons.

| # | Question | Options | Recommendation |
|---|---|---|---|
| D1 | **Who is locked in?** The user said "recorder". The payer can be somebody else. | (a) the recorder only, as said; (b) the payer only; (c) both | (a), as the user said. Consequence to confirm: *Alice cannot record "I paid the hotel for Bob and Carol" for herself* because she would have to take a share. Bob can record it, with Alice as the payer. |
| D2 | **Where is it enforced?** | (a) **UI only**: own checkbox ticked and disabled. (b) **UI and domain**: `Expense.record` also refuses an expense whose participants do not include `recordedBy`, with a new `ErrorCode` (suggested `RECORDER_NOT_PARTICIPANT`, 422). | (b). A UI lock is bypassed by any direct API call, and the project keeps rules in the aggregate (conventions 01 section 2.3). Cost of (b): a contract change (the new code on `recordExpense`), the code in `ErrorCode` and `frontend/src/lib/errors.ts`, and checking the existing tests. Cost if (a) is wrong: the rule can be broken through the API. |
| D3 | **Rule number.** | next free number is `R18`; extend R3 instead | `R18`, one sentence, and a note in R3 that it overrides "may or may not" for the recorder. The requirements file lives outside this repository (FAVS folder): update it there too. |

Not part of this fix, parked with its trigger: **correcting an expense** (R6 lets the recorder, the payer or an admin correct). `correct` does not exist yet; when it is built, decide whether the rule applies to the corrector. Existing expenses stay as they are, no migration.

## 5. Acceptance criteria (for option D2 b; for D2 a only 1 to 3)
1. The form shows the current user ticked and disabled in "Split equally between"; they cannot be unticked.
2. The share preview always includes the current user.
3. With the current user as the only participant the form still submits (one participant is allowed, R3).
4. `Expense.record` throws `DomainException(RECORDER_NOT_PARTICIPANT)` when `recordedBy` is not among the participants; nothing is saved and no `ExpenseRecorded` event is published.
5. `POST /groups/{id}/expenses` answers 422 with `code: RECORDER_NOT_PARTICIPANT` in that case (listed in `openapi.yaml` for the operation, with an example).
6. The message for the new code is in `messageFor` and has a test.
7. The checks keep their order; the new check goes with the participant checks (after `NO_PARTICIPANTS`), so the earlier errors are unchanged.

## 6. Where to work
- **Frontend:** `ExpenseForm.tsx` (disable the own checkbox; `excluded` never contains `currentUserId`), `Expenses.test.tsx` (new tests: own box disabled, preview includes me, other members still toggle).
- **Backend:** `Expense.record` and its Javadoc (add `RECORDER_NOT_PARTICIPANT`, R18), `ErrorCode`, `ExpenseTest` (domain unit test with the rejected case and "nothing published"), `ExpenseControllerTest` (422 and the code).
- **Contract (own commit, first):** `docs/api/openapi.yaml`, `recordExpense` responses; then `npm run gen:api`. Use `/contract-change`.
- **Tests that may break:** the backend tests build expenses with `participantIds` / `splitBetween` in 16 files (71 mentions: `ExpenseFixture`, `ExpenseRequests`, `ExpenseControllerTest`, `MallorcaSliceTest`, `SettlementRequests`, and others). Every place where the recorder is not a participant must be changed. Count them first; this is the main risk and decides the size of the fix.
- **Docs in the same change:** conventions 01 (vocabulary list for the Expense context, the new code), the requirements file (R18, R3 note), the domain diagram only if a rule is drawn there (check `docs/domain/ShareWise_Domain_Model.drawio`).

## 7. Order of commits
1. `contract(expense): recordExpense can answer RECORDER_NOT_PARTICIPANT` (only `openapi.yaml`)
2. `chore(frontend): regenerate api types`
3. `fix(expense): the recorder is always a participant` (domain rule, `ErrorCode`, tests, fixtures)
4. `fix(frontend): the recorder cannot untick themselves` (form, message, tests)
5. `docs(expense): R18` (conventions, requirements note)

Gates: `./mvnw verify` (Docker running), `npm run lint && npm run typecheck && npm test && npm run build`, `npx @redocly/cli lint docs/api/openapi.yaml`.

## 8. Out of scope
Percentage and exact splits (only EQUAL exists), correcting and removing expenses, any change to the payer rule (R3 stays: the payer may or may not be a participant).
