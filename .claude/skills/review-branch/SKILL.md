---
name: review-branch
description: Review a ShareWise branch before it is merged into main. Runs the gates, dispatches a fresh read-only subagent with reviewer-prompt.md (conventions, vocabulary, tests, comments, docs drift) and, for logic changes, the built-in /code-review for bugs; then triages and presents one list. Use when a feature, fix or refactor branch is ready, or when the user asks for a review of a branch.
---

# Review a branch before merging

Two reviews, two questions: **does it follow our rules** (a fresh subagent with `reviewer-prompt.md`) and **is it correct** (`/code-review`). The build already checks formatting, module boundaries and ArchUnit rules; the reviews cover what it cannot.

## Steps
1. **Scope:** the branch (default: the current one) and its base (`git merge-base main <branch>`). List the commits and the changed files grouped by backend, frontend, contract, migrations, docs. If the branch touches nothing but `.md`, skip to step 4 and review it yourself.
2. **Gates first:** run the push gate's checks (`node .githooks/gate.mjs pre-push < /dev/null` from the repo root; Docker must run). A red gate ends the review: report what failed.
3. **Dispatch in parallel:**
   - a fresh `general-purpose` subagent on `sonnet` with `reviewer-prompt.md` (this folder), brief filled in: branch, base, the changed files;
   - `/code-review` on the same diff when the branch changes behaviour (backend `main` or frontend `src`), not for docs or tests only.
4. **Triage in the main session:** open every cited file:line and confirm it; drop findings the code contradicts. Merge duplicates. Apply the same **proportion** as `/edge-case-probe`: a finding that cannot happen through the real app, or whose fix is a new mechanism for a small harm, is accepted or noted, not fixed; a duplication finding needs both places and a smaller way. Sort into must (blocks the merge), should, nit.
5. **Present:** a short overview first (what the branch does, how the parts fit), then the findings table, then a recommendation (merge / fix first). The user decides which to fix.
6. After the fixes: run `/review-doc` so the user has the jump-start guide for their own review, then `git switch main && git merge --no-ff <branch> -m "merge: <branch> (<one line>)"` only when the user says so.
