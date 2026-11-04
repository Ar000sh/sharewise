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

## Proportion: only what can really happen (read this before anything else)
ShareWise is a study project: a few users per group, one team, one semester. The aim is good code, not every conceivable defence. A failure mode earns code or a test only when **all three** hold:
1. **It can happen through the real app**, in ordinary use or by clicking around, or it has a security or data-loss impact.
2. **You can say who hits it and roughly how often.** Write that in the failure-modes table; "an attacker could" is not enough.
3. **The fix is small next to the harm**: a one-line rule, a lock the code already uses, one test. If the fix is a new mechanism (an index, a sweep job, a retry, a second table, a defensive layer), accept or park the case, unless the harm is lost data or a security hole.

Rules of thumb:
- A state that no operation can produce yet (a status nothing sets, an operation that does not exist) is **parked with a trigger**, not defended and not tested on its own.
- **No thread-based tests.** Test the rule one call after another; the lock or the constraint does the rest.
- No defensive check for what an earlier step already guarantees.
- Each rule is tested **once**, at the lowest level that can show it (`docs/conventions/02`).
- **"Handled by design", "accepted" and "parked" are good answers.** A long list is not the goal: rank, show at most about 10, and put the rest in one line.
- When two fixes exist, take the smaller one and say what it costs.
- A finding that only holds through a bug elsewhere or a future operation is a **known limit**, not a finding.

(The notifications design first listed 35 failure modes, a stored-expiry sweep with an extra index and a thread test; most of it was dropped after the user asked whether it was really necessary. This section exists to prevent that.)

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
2. Write the doc's **Failure modes** section: per risk, who hits it and how often, what happens and what stops it (domain rule, service, database constraint, UI). Apply the proportion test above before a risk gets a row.
3. Mark each: **handled by design**, **needs a test** (goes into the plan from the start), **accepted** or **parked** (with why and the trigger).
4. Show the user the list ranked by impact × likelihood (at most about 10 rows); they decide the scope.

## ATTACK mode
1. **Brief, without our opinions:** the design doc path, the public interface (controllers, `api` ports, the relevant `openapi.yaml` paths), the rules in scope (R-numbers), how to run tests. Do NOT pass our conversation, what we already tested, or where we think it is weak.
2. Tell the user in one line that an independent agent is attacking the code (roughly 100-150k tokens, a few minutes, Docker must run).
3. Dispatch one fresh `general-purpose` subagent on `opus` with `attacker-prompt.md` (this folder), brief filled in.
4. **Triage in the main session:** re-run each claimed probe yourself; drop what does not reproduce. Then apply the **proportion test**: a finding that cannot happen through the real app, or whose fix is a new mechanism for a small harm, becomes a known limit or is accepted, not a fix. Rank the rest by impact × likelihood and say which findings our own tests had missed.
5. **Fix the top 1-3 that pass the proportion test, test-first** (red, then green), each with a regression test at the right level (`docs/conventions/02`). Move a useful probe into the real test package and rewrite it in our style; do not keep probe code as is.
6. **Delete the probe folders** (`backend/src/test/java/de/hsh/favs/sharewise/probe/`, `frontend/src/probe/`). They are gitignored but would still run in the push gate.
7. **Park the rest** in the design doc under "Parked" with why. Report in four lines: found, fixed, parked, known limits.
8. Add a row or evidence to `docs/devsetup/CATALOG.md`: what the attacker found that we would have missed.
