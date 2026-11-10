---
name: review-doc
description: Write the jump-start review guide for a finished ShareWise feature branch at docs/review/<feature>/REVIEW.md, so the user can start their own code review: before and now in two sentences, a table of the features with their entry points (backend controllers and listeners, frontend components and route handlers) and the test to read, one small diagram, the few decisions to know before reading, diff and run commands, open points. Short on purpose (about 60 lines), every name checked against the code. Use when a feature branch is ready for the user's review, after /review-branch, or when the user asks for a review guide or review document.
---

# Review doc: a jump-start for the user's own review

The guide helps the user **start** reviewing; it is not a report. If it is long it becomes a hurdle of its own (the first versions of the notifications guide were rewritten four times because they were too long). The user follows the paths themselves: backend entry points are **controllers** (HTTP) and **listeners** (events), frontend entry points are **components**, which call a Next.js route handler, which forwards to a backend controller.

## Steps
1. **Scope.** The branch and its base (`git fetch origin main` then `git merge-base origin/main <branch>` (merges happen on GitHub, so the local `main` can be stale)); `git log --oneline <base>..<branch>`; the changed files grouped by backend, frontend, contract, migrations. Decide the **feature name** for the folder (`docs/review/<feature>/REVIEW.md`, lower case, one word or hyphenated). One REVIEW.md per feature branch, not per topic.
2. **Find the features the user can name** ("invite a person", "accept an invitation", "read the inbox"), not file names. For each, find the entry point by reading the code: the controller method (`@GetMapping`, `@PostMapping`), the `@ApplicationModuleListener`, the page or component, and the route handler it calls. Add the first two or three steps of the path (controller → service → the domain method that holds the rules). The user does the rest.
3. **Write the file from `template.md`** (this folder). Rules for the content:
   - **About 60 lines, at most about 80.** If it grows, cut: history tables, decision catalogues, per-file lists, copied code and long explanations do not belong here. The design doc and `git log` hold the rest; link them.
   - **Features table first**: Feature | Start at (backend) | Frontend | Test to read. One row per thing a user can do.
   - **One small diagram** (a sequence of the main flow, or a state diagram), Mermaid. Nothing else.
   - **"Know these before you read"**: at most six bullets; only decisions a reviewer would otherwise question, each with its reason in one clause.
   - **Diff and run**: three or four commands, including the contract diff as a good first read and `git diff -w --ignore-cr-at-eol` (the repo mixes CRLF and LF).
   - **Open**: at most four bullets (unverified, parked, waiting for another branch).
4. **Verify before you save.** Every class, method, endpoint and file the guide names must exist: check with `ls`/`grep` (a short script is fine). Every commit hash must exist. Test results (counts, "green") only if you ran them in this session, with what you ran. Do not state anything you have not checked.
5. **Commit** as `docs(shared): review guide for <feature>` and tell the user in two lines: the path, and how to see the whole branch against `main` in the editor (GitLens: Compare References with the working tree included; open the right-hand working file for jump-to-definition, which needs the language extensions installed).

## Do not
- Do not paste the design doc, the decisions list or the changelog into it.
- Do not list every file or every endpoint of the repo; only the ones this branch is about (mark the existing features it touches in one row).
- Do not redo it from scratch when the branch changes: edit the rows that changed.
