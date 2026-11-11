# Retro log

One entry per retrospective (`/retro`), newest first: trigger, findings with where each went, and follow-ups for next time. The next retro starts by checking the last entry's follow-ups.

## 2026-11-04: the notifications feature (usernames, invitations to accounts, inbox)
**Trigger:** the feature branch was built and reviewed; the user asked for a retro of the setup. `/retro` is manual-only, so it was run by reading `SKILL.md` at the user's explicit request. **Result:** 9 findings, 5 built (the user approved 1 to 5), 1 not approved, 3 later.

**Last retro's follow-ups:** the git gates blocked nothing real (pre-push takes 4 to 6 minutes); `/edge-case-probe` plan mode and the design review found real flaws before code, `/review-branch` and `/code-review` found convention gaps and an N+1 query; explanations were not the repeated correction this time, the review document format was; finding 8 (domain sources outside the repo) is still held.

| # | Finding | Evidence | Went to | Decision |
|---|---|---|---|---|
| 1 | The edge-case skills were too strict and led to unnecessary mechanisms | stored-expiry sweep plus unique index, 35 failure modes, a thread test; the user asked "is it really necessary?" | `edge-case-probe` `SKILL.md` and `attacker-prompt.md`: proportion rules | Built |
| 2 | Duplication was found only when the user read the code | five copies of a filter, a double check, five error paragraphs, four bell actions, a case at two test levels | `docs/conventions/03` section 7, root `CLAUDE.md`, definition of done | Built |
| 3 | The branch reviewer was not asked about duplication or over-engineering | same evidence | `review-branch/reviewer-prompt.md` and the triage step | Built |
| 4 | The review document was redone four times | user feedback: too long, "a hurdle on its own", then the agreed jump-start format | skill `review-doc` with `template.md`, called after `/review-branch` | Built |
| 5 | Line-ending flips (CRLF to LF) inflated diffs three times | commit `0377fe5` and two more fixes, from `sed -i` and file rewrites | commit gate check `.githooks/line-endings.mjs` with a test and an escape hatch | Built |
| 6 | Stale build output from another branch caused false failures | `target/classes` held a `V204` from the settlements branch, stale `.next/dev` types | one line in `02_TESTING` pitfall 4 and `RUNNING.md` | Not approved (the user chose 1 to 5) |
| 7 | A session ended with uncommitted work in a dirty tree | a handoff file was needed to carry it over | root `CLAUDE.md` line: end a session with validated work committed | Later (one occurrence) |
| 8 | The rule "errors through `ErrorBanner`" was not followed in five places | convention 04 section 4 against the code | a scan test like `themeRules` | Later (rows 2 and 3 cover most of it) |
| 9 | Low memory killed background jobs twice | two killed `verify` runs with Docker, Testcontainers and the Java server | `RUNNING.md` note: run `verify` in the foreground, stop the dev containers if memory is tight | Later (environment-specific) |

**Follow-ups for the next retro:** Does the failure-modes list of the next design stay under about 10 rows? Did `/review-branch` report duplication, and was it proportionate? Did the line-ending gate ever block a legitimate commit? Was the first `/review-doc` usable without a rewrite? Re-check rows 6 to 9: did any of them hurt again?

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
| 8 | Domain sources (diagram, R1-R17) live outside the repo | Teammates and a fresh clone cannot open them | Diagram moved earlier (`docs/domain/`); on 12.11.26 the requirements and the glossary followed, next to it. The FAVS folder keeps a pointer file at each old path | Built |

**Follow-ups for the next retro (after Ex5):** Did the gates ever block something real, and how long does pre-push take? Did `/review-branch` or `/edge-case-probe` find anything the build missed? Were explanations still asked for repeatedly? Decide finding 8.
