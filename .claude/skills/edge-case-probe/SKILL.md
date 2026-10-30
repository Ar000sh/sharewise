---
name: edge-case-probe
description: Find edge cases and failure modes in ShareWise in two modes. PLAN mode (design time, no code) walks the checklist over a design doc and fills its "Failure modes" section. ATTACK mode (after building) dispatches a fresh subagent with attacker-prompt.md, which sees only the spec and the public interface and tries to break the code; then triage, fix the top 1-3 test-first, park the rest. Use plan mode when writing a design doc, attack mode before calling a module, slice or exercise done, and whenever the user asks about edge cases or hardening.
---

# Edge-case probe

Why two modes: tests written by the author only cover what the author thought of. Plan mode moves problems into the design; attack mode uses a fresh agent that has not seen our reasoning. (In the `workflows` repo the first fresh attack found 13 reproduced issues that 100 % coverage and our own probe had missed.)

| Situation | Mode |
|---|---|
| Design doc drafted, no code yet | **Plan** |
| A module, slice or exercise is built and green | **Attack** |
| Small change to an already-probed module | Neither: add a regression test |

## The ShareWise risk areas (start here in both modes)
- **Money:** exact splits (remainder cents, percentages that round), balances summing to 0, settlement larger than the debt (R17).
- **Lifecycle:** every operation in every group status (OPEN, CLOSED_FOR_SETTLEMENT, ARCHIVED); auto-archive at zero balances; reopen.
- **Membership:** late joiners (R10), leave/remove only at balance 0 (R11), last admin (R12), expired invitations (R9).
- **Events:** idempotent listeners, order, a listener failing halfway, eventual consistency of balances.
- **Concurrency:** two writes on the same aggregate (two settlements, double accept, correct vs remove).
- **Authorization:** non-members, former members, ids from another group.
The full checklist is in `attacker-prompt.md` (this folder).

## PLAN mode
1. Read the design doc and the rules it names. Walk the risk areas and the checklist over each component and flow.
2. Write the doc's **Failure modes** section: per risk, what happens and what stops it (domain rule, service, database constraint, UI).
3. Mark each: **handled by design**, **needs a test** (goes into the plan from the start), or **parked** (with why).
4. Show the user the list ranked by impact × likelihood; they decide the scope.

## ATTACK mode
1. **Brief, without our opinions:** the design doc path, the public interface (controllers, `api` ports, the relevant `openapi.yaml` paths), the rules in scope (R-numbers), how to run tests. Do NOT pass our conversation, what we already tested, or where we think it is weak.
2. Tell the user in one line that an independent agent is attacking the code (roughly 100-150k tokens, a few minutes, Docker must run).
3. Dispatch one fresh `general-purpose` subagent on `opus` with `attacker-prompt.md` (this folder), brief filled in.
4. **Triage in the main session:** re-run each claimed probe yourself; drop what does not reproduce. Rank by impact × likelihood and say which findings our own tests had missed.
5. **Fix the top 1-3 test-first** (red, then green), each with a regression test at the right level (`docs/conventions/02`). Move a useful probe into the real test package and rewrite it in our style; do not keep probe code as is.
6. **Delete the probe folders** (`backend/src/test/java/de/hsh/favs/sharewise/probe/`, `frontend/src/probe/`). They are gitignored but would still run in the push gate.
7. **Park the rest** in the design doc under "Parked" with why. Report in four lines: found, fixed, parked, known limits.
8. Add a row or evidence to `docs/devsetup/CATALOG.md`: what the attacker found that we would have missed.
