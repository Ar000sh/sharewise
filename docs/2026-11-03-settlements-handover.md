# Settlements: handover after the build, the attack and the review (2026-11-03)

Branch `feature/settlements` (HEAD at the time of writing: the commit that adds this file). Not merged. Spec: `docs/2026-11-02-settlements-design.md`, plan: `docs/2026-11-02-settlements.md`.

## 1. What the branch does
97 files, about 7,900 added lines.

- **Domain:** `SettlementPlan` computes the fewest transfers (R15): exact (bitmask DP) up to 16 people, greedy above. `SettlementPayment` is a new aggregate with `record`, `confirm`, `reject` and `waive`.
- **Reservations:** a PENDING payment reserves part of the open debt (`OpenDebtPolicy`, R17). The ledger row is locked while recording or waiving (D6), `@Version` guards concurrent answers.
- **Events:** asynchronous listeners book confirmed payments and waivers into the ledger, idempotently. `shared.events` (`MemberBalanceStatusChanged`, `AllBalancesSettled`) is the one new exception to the module rules (`ConventionTests` enforces exactly those two).
- **Group side:** `Membership.balanceStatusAt` (migration group V204) ignores a stale zero-balance status.
- **Frontend:** `SettleUpTab` (plan, "I paid"), `SettlementInbox` and `PendingBadge` (confirm/reject, polled every 30 s and after an answer), `WaiveDebtForm`, `Dashboard` (home page body), the shared `useLookAgain` hook, proxy routes.
- **Tests:** domain, service, API and database tests, `MallorcaSliceTest` (Carol pays Alice 270.00, Alice confirms: Alice +360.00, Bob -90.00, David -270.00, Carol 0.00; a waiver of Bob's 90.00 gives Bob 0.00, Alice +270.00; a rejected payment books nothing), Playwright settle-up journey.

## 2. State of the checks
- Push gate green on 2026-11-03: backend `verify` (300 tests), frontend lint, typecheck, tests (151), build, contract lint, guard hook tests.
- `npm run e2e`: 2 of 2 passed.
- Slice test: red check done (without `@ApplicationModuleListener` on `SettlementPaymentConfirmedListener` the test times out).

## 3. Open decisions (yours)
These come from the independent attack (design doc section 8c). Our own tests had missed all of them.

| Id | Severity | Finding | Options |
|---|---|---|---|
| A1 | high | A PENDING payment is confirmed after a later plain expense has already zeroed the pair. Booking it makes the creditor negative (Bob owes Alice 50.00 and records it; Bob then pays a 50.00 expense for Alice; Alice confirms; Alice ends at -50.00). `OpenDebtPolicy` caps only at `record` time. | (a) confirm refuses with a clear error when the open debt is gone (recommended: keeps the rule "no balance crosses zero" as written); (b) cap the booked amount; (c) reword the rule. Reproduce: probe test `confirmingAPendingPaymentAfterALaterExpenseMakesTheCreditorADebtor` (deleted, rewrite from this text). |
| A2 | medium | `AllBalancesSettled` is published while a PENDING payment is still open. The parked auto-archive (L5) would archive a group with an open payment. | Publish only when no payment is PENDING. Decide before L5 consumes the event. |
| A3 | medium | After a plan reshuffle the plan shows an edge the server always refuses with 422 `PAYMENT_EXCEEDS_OPEN_DEBT` (Bob has 50.00 PENDING to Alice; Carol pays 50.00 for Alice; plan shows Bob to Carol 50.00, `pendingAmount` 0.00). `pendingAmount` only counts the pair, the person cap is also used up. | The plan counts the person-level reservation. |

## 4. Review findings still to fix
Confirmed by reading the code. Suggested: one fix agent for all "should" items, then a scoped re-review.

| Severity | Where | Finding |
|---|---|---|
| should | `SettlementPaymentService.java:142` | `awaitingMyAnswer` makes two group lookups per pending payment (`groups.status`, `groups.isMember`); N+1 on an endpoint polled every 30 s. Use one port call for the caller's groups. |
| should | `SettlementPaymentRepository.java:25` | The `reserved` JPQL holds the reservation rule and reads `LedgerEntry` of another aggregate (01 section 2.1 / 2.3). Name rule D5 in the Javadoc as a deliberate exception, or split the query. |
| should | `SettlementPaymentRepository.java:25` | `pendingAmount` also counts CONFIRMED and WAIVED payments the ledger has not booked yet, so a waiver shows "awaiting confirmation" for a moment. Contract text says "PENDING" only. Fix the wording or exclude them in the plan. |
| should | `Group.java:150` | `updateBalanceStatus` returns void, but `.claude/rules/backend-domain.md` and conventions 01 section 3.1 say each operation returns its event. Change the rule text in the same commit. |
| should | `MySettlementPaymentsController.java:41` | `GET /me/settlement-payments` answers 400 for `role=debtor` and `status=CONFIRMED|REJECTED|WAIVED`, which the contract allows. Narrow the contract (own commit) or implement the filters. |
| should | `SettlementPayment.java:140` | `reject` overwrites the debtor's note. A separate `resolution_note` would keep both (needs a contract change). |
| low | `SettleUpTab.tsx:33` | `PaymentForm` lets 0 or negative amounts through to the server (`WaiveDebtForm` checks). |
| low | `Membership.java:96` | The out-of-order guard uses the wall clock: equal instants or a clock step can leave the zero flag wrong. A per-ledger sequence would be deterministic. |

## 5. Parked
- A6: the note length is counted in UTF-16 units by Bean Validation, in characters by the contract (300 emoji are refused).
- A7: `confirm` and `reject` answer 422 `GROUP_NOT_OPEN` for an archived group; the contract lists no 422 (needs `/contract-change`). The UI text for that code says "no longer open for new expenses".
- A9: the contract's Amount pattern accepts `010.00`; the code refuses leading zeros. Tighten the pattern in a contract commit.
- Above 16 non-zero people the plan is greedy, nothing tells the caller (R15 not guaranteed).
- The balance mapping from `ledgers.totals` to `List<MemberBalance>` is copied in three places.
- Not probed: former members (no leave operation yet), out-of-order `MemberBalanceStatusChanged`, HTTP-level concurrency, a listener failing halfway (F4, parked by design).
- UI: note limit 200 characters vs API 500; no route-handler tests for the new proxy routes (siblings have none either); `aria-label` on the badge span should be `role="status"`; duplicate fetches of the pending list; the inbox error has no retry; re-clicking "Waive" on a row discards the typed note.
- Nits: unsorted imports in `RecordSettlementPaymentRequest` and `WaiveDebtRequest`; commit subjects over 72 characters (3670df0 105, c1dc683 95, a3a227b 81, 5c3c873 74, 10f035e 73); stale "Ex5" comment in migration V401 (do not edit, checksum); old `Thread.sleep(300)` in `ExpenseRecordedListenerTest.java:71` (not from this branch).
- Suggested follow-up commits: one contract cleanup (plan example order, `pendingAmount` wording, `/me/settlement-payments` enums, `NotFound` text, 422 on confirm/reject, Amount pattern); a chore to normalise the CRLF files; a cleanup of the deferred minors (rename `BalanceStatusListener` to `MemberBalanceStatusChangedListener`, stale "Task 5" comment, `02_TESTING` section 6 drift, missing tests).

## 6. Needs the team or the professor
- Auto-archive (L5) and who consumes `AllBalancesSettled`.
- Diagram changes: `waive` becomes a factory that creates a WAIVED record (changes its meaning, not only its signature), `updateBalanceStatus` takes `occurredAt`, `Membership.balanceStatusAt`, the `shared.events` exception.
- R15: exact minimum up to 16 people, greedy above (our choice, not from the slides).
- R16 says confirmation is optional in the MVP; our choice to include it is D1 in the design doc.

## 7. Rulings I made (with what each costs if wrong)
1. Worked on `feature/settlements` in place, no worktree. Cost: switch to a worktree later.
2. `RecordSettlementPaymentCommand(groupId, caller=debtor, toCreditor, amount, note)` and `WaiveDebtCommand(groupId, caller=creditor, fromDebtor, amount, note)`; the plan left the fields open.
3. The repository returns `BigDecimal` (JPQL sum, coalesce 0), `OpenDebtPolicy` wraps it in `Money.eur`. Cost: the plan said `Money`.
4. One API test may use `@MockitoBean` on `GroupMembership#status` (archived group). Cost: that test is less real.
5. Plan exactness test: the plan's example does not tell DP from greedy (greedy also gives 3), so the reviewer's case A+3, B+3, C-6, D+5, E-5 (optimum 3, greedy 4) replaced it. Cost: none known.
6. A CRLF conversion that a fix introduced was treated as Important although the re-reviewer rated it Minor. Cost: one extra small fix round.
7. `errors.ts` was already CRLF on main, kept as is. Cost: churn if it is ever converted.
8. `reject` replaces the debtor's note (the contract has one `note` field). Cost: the original remark is lost; fix needs a contract change.
9. Task 4 commits carry the trailer "Claude Opus 5.5" (CLAUDE.md: the model that did the work). Cost: amend the text.
10. Task 5 `redeliveringTheConfirmedEventBooksOnce` calls the booking twice instead of re-publishing the event. Cost: no end-to-end redelivery test through the event registry.
11. The booking skeleton is duplicated three times (`bookExpense`, `bookSettlementPayment`, `bookDebtWaiver`) and left as is. Cost: a later refactor.
12. `Group.updateBalanceStatus` takes `occurredAt`, `Membership` stores `balance_status_at` (nullable migration group V204); older events are ignored, equal timestamps apply. Cost: one extra column and a signature that differs from the diagram.
13. Added `SettlementPlanService` (the plan listed only controllers). Cost: one extra class.
14. `GET /me/settlement-payments` supports only `PENDING` and `role=creditor`, other values answer 400. Cost: contract mismatch (see section 4).
15. Frontend uses the contract type `SettlementPlanEdge`, the brief said `SettlementPlanEntry`. Cost: none.
16. The settle-up tab shows the whole plan to everybody (You owe / Owed to you / Others), all proxy routes were created in Task 8.
17. UI wording without gendered pronouns. Cost: a copy change.
18. Inbox above the plan in the Settle up tab, badge on the tab label, on the home page a total badge plus the inbox only when something is pending; the badge polls with a plain `setInterval`. Cost: a layout tweak.
19. The home page body moved into the client component `Dashboard.tsx` so `page.tsx` stays a server component. Cost: a small refactor.
20. Attack triage: A4, A5, A8 fixed now; A1 to A3 parked for you; A6 and A7 parked.
21. The code refuses leading zeros in amounts (`010.00`), stricter than the contract pattern. Cost: a contract-valid amount is refused.
22. Environment: I restarted Docker Desktop (`wsl --shutdown`) and stopped four stale Maven/Surefire Java processes after you allowed it.

## 8. How to continue
1. Decide A1 to A3.
2. Dispatch one fix agent for section 4, then a scoped re-review.
3. Final whole-branch review (most capable model), one fix wave.
4. `superpowers:finishing-a-development-branch`; merge with `--no-ff` only when you say so.

The working notes (ledger, briefs, reports, review packages) are in `.superpowers/sdd/2026-11-02-settlements/` (git-excluded: `git clean -fdx` deletes them).
