# Retro log

One entry per retrospective (`/retro`), newest first: trigger, findings with where each went, and follow-ups for next time. The next retro starts by checking the last entry's follow-ups.

## 2026-10-30: Ex4 and the agentic setup (done by hand, before `/retro` existed)
**Trigger:** Ex4 merged; the setup was reviewed against the `workflows` repo. **Result:** 8 findings, 7 built, 1 held.

| # | Finding | Evidence | Went to | Decision |
|---|---|---|---|---|
| 1 | Parallel tracks drifted into four codebases | Different layouts, names and test styles at the Ex4 merge; an alignment refactor followed | `docs/conventions/` + `ConventionTests` | Built (2026-10-27) |
| 2 | Stale rule documents misled readers | `work/TESTING_STYLE.md` (FAVS) still described seeded users and the dev profile; `docs/tracks/` still listed `MemberId` | Archived with a pointer to the current document | Built |
| 3 | Claude never saw the Next.js 16 warning | It sits in `frontend/AGENTS.md`; Claude Code reads only `CLAUDE.md` | `frontend/CLAUDE.md` imports it | Built |
| 4 | Coding sessions loaded study notes instead of coding rules | About 54 KB of FAVS notes auto-loaded, conventions not | Per-side `CLAUDE.md` + `claudeMdExcludes` (local) | Built |
| 5 | Rules were trust-only (green main, contract alone, migrations, generated types, lock file) | Written in conventions, no check | Git gates + guard hook | Built |
| 6 | Explanations were asked for again and again ("I don't understand point 8", "why did you remove Lombok") | Several times in the Ex4 review sessions | Root `CLAUDE.md`: explain the why, overview first, "who decides what" | Built |
| 7 | Commit trailer named the wrong model | Conventions said Sonnet, sessions ran Opus | Conventions 03: name the model that did the work | Built |
| 8 | Domain sources (diagram, R1-R17) live outside the repo | Teammates and a fresh clone cannot open them | Held: moving them changes the single source of truth in the FAVS folder; the user decides | Waiting |

**Follow-ups for the next retro (after Ex5):** Did the gates ever block something real, and how long does pre-push take? Did `/review-branch` or `/edge-case-probe` find anything the build missed? Were explanations still asked for repeatedly? Decide finding 8.
