---
name: retro
description: Retrospective on the setup after an exercise, a slice or something that went badly. Collects what hurt, what worked and what the user kept asking, sorts each finding into hook, skill, rule, convention, agent or nothing, challenges each one, prunes the setup, and logs the outcome in docs/devsetup/retro-log.md and CATALOG.md. Manual only.
disable-model-invocation: true
---

# Retro: improve the setup, not just the code

Optional argument: a focus ("tests", "explanations", "cost", ...). **Nothing is built until the user has answered item by item.**

## 1. Collect
Read the conversation, `git log` since the date of the last entry in `docs/devsetup/retro-log.md`, that entry's follow-ups, the "Parked" lists of the recent design docs, and `docs/devsetup/CATALOG.md`. Look for:
- **What hurt:** bugs found late, rework, wrong assumptions, doc/code drift, gates that were slow or noisy, near misses.
- **What worked:** what caught problems early and should be repeated on purpose.
- **What the user asked or corrected repeatedly:** a teaching gap or an unclear rule.
- **Repeated manual steps:** done three times → skill candidate.
- **Decisions made in chat** that are not in a design doc, the conventions or the FAVS requirements.

## 2. Sort each finding
| Destination | When |
|---|---|
| Git gate (`.githooks/gate.mjs`) | Must hold for every commit or push, people included, no judgment |
| Guard hook (`.claude/hooks/guard.mjs`) | Must never be done by Claude, no judgment. Needs a test |
| Convention (`docs/conventions/`) | A rule for the code, for people and Claude; plus an ArchUnit test if it can be checked |
| Root, `backend/` or `frontend/` `CLAUDE.md` | How we work, for that scope. One line; delete what stopped mattering |
| `.claude/rules/` | A rule that only matters for one kind of file |
| Skill | A repeatable procedure that needs judgment |
| Agent | Only with a reason: isolation, fresh eyes, a cheaper or stronger model, restricted tools, a role used often |
| Design doc "Parked" | Good idea, not now |
| Nothing | One-off or already covered. Say so |

## 3. Challenge every item (one line each)
Evidence (what happened; none = hypothesis, label it), scope (backend, frontend, both, only some changes), cost (time per run, noise, tokens), now or later, existing coverage.

## 4. Review the setup itself
For each CATALOG row: did it help since it was added? Propose removing or shrinking what never fired or only added friction. Check the CLAUDE.md files and rules for stale, duplicated or too long entries. Note where the model or the amount of context was wrong for a task.

## 5. Present, then ask once
A table: #, finding, evidence, destination, cost, recommendation (build now / later / drop). At most about 10 rows. One question: which to build, change or drop (the user answers by number).

## 6. Build what was approved
Hooks and gates get tests. Add CATALOG rows. Append the retro to `docs/devsetup/retro-log.md` (date, trigger, findings with destination and decision, follow-ups for next time). Commit as `chore(devsetup): ...` through the gates.

Do not spawn agents for the retro itself; it is a conversation with the user.
