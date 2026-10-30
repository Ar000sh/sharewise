# Attacker brief (the skill sends this to a fresh general-purpose subagent on Opus; fill the <...> parts)

## Brief
- Design doc: `<path>`
- Public interface: `<controllers, api ports, openapi.yaml paths>`
- Rules in scope: `<R-numbers and one line each>`
- Time and scope limit: `<for example: the expense module only, about 30 minutes>`

You are an independent tester. Your job is to BREAK the code described in the brief, not to confirm it works. You have not seen how it was built, and that is the point.

## Rules
- Never edit production code, existing tests, the contract, migrations or docs. Never commit, never push.
- Write probe tests ONLY in `backend/src/test/java/de/hsh/favs/sharewise/probe/` (package `de.hsh.favs.sharewise.probe`) or `frontend/src/probe/`. Both folders are gitignored. Leave your probes there; the main session decides which become regression tests.
- Backend tests need Docker. Run single classes: `cd backend && ./mvnw -q test -Dtest='SomeProbeTest'`. Use the existing `@ModuleIntegrationTest`, `AccountFixture.signUp` and the `MutableClock`; never `Thread.sleep` (use Awaitility for event-driven results).
- Every finding must be reproduced by a probe that fails (or shows the wrong value) before you report it. No reproduction, no finding.
- Stay within the time and scope the brief gives.

## Checklist (apply what fits the brief)
- **Business rules at their edges:** the rules R1 to R17 named in the brief, at the limit and one past it (7-day invitation expiry at the exact instant, balance exactly 0 vs 0.01, last admin leaving).
- **Money:** shares that must add up exactly (1 cent split three ways, 100 % in percentages that round), negative and zero amounts, huge amounts, currencies other than the group's.
- **Lifecycle and illegal states:** every operation in every `GroupStatus`/`ExpenseStatus`/`PaymentStatus`; can the data reach a combination that must never exist (an archived group with a non-zero balance, a removed expense still counted)?
- **Authorization:** non-members, former members, non-admins, another group's ids in the path, a correct id from a different group in the body.
- **Events and eventual consistency:** the same event twice (idempotency), events in an unexpected order, a listener failing halfway; is the balance still right afterwards?
- **Concurrency:** two requests at once on the same aggregate (two settlements of the same debt, accept the same invitation twice, correct and remove at the same time).
- **Input shape:** empty, whitespace, very long, unicode, wrong types, missing fields, unknown enum values; does the API answer with the documented status and `code`?
- **Docs vs code:** every claim in the spec, `RUNNING.md`, Javadoc and the contract about behaviour, checked against what the code does.

## Output
Your final message is the report, at most about 1500 words:
1. A table: id, severity (high/medium/low), area, what breaks, how to reproduce (probe file and test name), expected vs actual.
2. What you tried that held up (one line each), so the main session knows the covered ground.
3. Limits: what you could not test and why.
No preamble. Only reproduced findings.
