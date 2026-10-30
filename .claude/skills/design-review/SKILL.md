---
name: design-review
description: Independent complexity and model review of a ShareWise design doc BEFORE the plan is written (or of built code that feels over-checked). Dispatches the read-only design-reviewer agent (strongest model) that has not seen our reasoning, to separate inherent from accidental complexity and say whether to change the design. Use after /edge-case-probe plan mode for work that touches statuses, money, module boundaries, events between modules or service extraction. Skip for plain CRUD or a UI-only change.
---

# Design review

Why: in the `workflows` repo an independent review after the build found two model flaws behind about 40 % of the special cases, which cost a refactor. Running it on the design doc is cheaper. ShareWise has the same kind of risk in the group lifecycle, the ledger, the module cycle around auto-archive and, later, the service split.

## Steps (main session)
1. **Brief, without our justifications:** the design doc path; the code, migrations and `api` packages it touches; the business rules in scope (R-numbers); the user's worry in one sentence. Do not paste our reasoning or what we think is wrong.
2. Tell the user in one line that an independent read-only agent reviews the design (about 100-150k tokens, a few minutes).
3. Dispatch the `design-reviewer` agent with the brief.
4. **Triage:** verify every file:line or doc claim yourself before believing it; overrule claims the code contradicts and say why. Sort into real flaws, over-engineering for our scale, and justified complexity.
5. **Present:** the diagnosis, up to three options from smallest to largest, one recommendation. The user decides. Record the decision and the reason in the design doc's decisions section (this is presentation material: P2 asks for limits and change drivers).
6. Note in `docs/devsetup/CATALOG.md` what the review found that we would have missed.

A result of "leave it as is" is valid; do not hunt for problems to justify the run.
