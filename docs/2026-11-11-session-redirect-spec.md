# Spec: a 401 anywhere sends the visitor to sign in

**Date:** 11.11.26 · **Type:** fix (code does not follow a convention) · **Branch:** `fix/session-redirect` (worktree `.claude/worktrees/fix-session-redirect`, from `origin/main` at `d8f91ac`) · **Status:** ready to build · **Found by:** the design review of `docs/2026-11-11-live-group-views-design.md` (lives untracked in the main checkout until its own branch)

This file is the hand-off to the session that implements it. Facts marked *(verified)* were read from the code at `d8f91ac`; the rest are decisions.

## 1. The problem
`docs/conventions/04_FRONTEND.md` line 35 says: *"A `401` anywhere sends the visitor to `/login?next=<path>`."* The code does this only for the very first call of a page. Every later 401 (the session expired, the refresh token was revoked, the user signed out in another tab) shows the banner "You are not signed in. Please sign in." with no link, and the page stays as it is.

It is fixed before `feature/live-group-views` because that branch adds a 10 s poll of the open group page: with the 401 handled in one place, the poll needs no code of its own for a signed-out visitor.

## 2. What happens today *(verified)*
| Where | Behaviour |
|---|---|
| `frontend/src/lib/api.ts` `api()` lines 17-30 | Throws `ApiError(401, "UNAUTHENTICATED")`, nothing else. Every browser call goes through it. |
| `frontend/src/lib/currentUser.tsx` lines 9, 20-30 | The only redirect: on 401 from `/me` (once per page load) `window.location.replace("/login?next=" + encodeURIComponent(pathname + search))`. Skips the public path `/login`. |
| `frontend/src/components/InboxBell.tsx` lines 137, 140-146 | On 401 sets `stopped` and never polls again (otherwise it would repeat the 401 every 30 s). |
| `frontend/src/components/ErrorBanner.tsx` | Shows `messageFor(error)`; for 401 that is the sentence above (`lib/errors.ts` line 4). |
| `frontend/src/app/login/page.tsx` line 6 | Reads `next` through `safeReturnTo` before it goes anywhere (F5). |
| `frontend/src/lib/session.ts` | Server module (imports `oidc`, cookies); `safeReturnTo` lives here. |
| `frontend/src/lib/backend.ts` (`proxyToBackend`) | Answers 401 `UNAUTHENTICATED` when there is no cookie or the refresh failed (and clears the cookie). |
| Sign-out (`AuthBar.tsx` line 19) | A real form post to `/api/auth/logout`, not through `api()`: unaffected. |

## 3. Wanted behaviour
**Any call through `api()` that gets a 401 sends the visitor to `/login?next=<current path and query>`, and still throws**, so the calling code ends as it does for any error. After signing in the visitor is back on the same page.

## 4. Decisions
| # | Decision | Why |
|---|---|---|
| S1 | The redirect lives **once, in `api()`** (`lib/api.ts`). | One place per rule (`03` section 7); every browser call already passes there. (call) |
| S2 | `currentUser.tsx` loses its own redirect, `InboxBell` loses `stopped`. | They become dead special cases of S1. The bell's poll ends because the page is left. |
| S3 | ~~Only the first 401 of a page redirects (a module-level flag).~~ **Dropped (user, 11.11.):** every 401 calls `replace`. | Several 401s at once all `replace` to the same address; no history entry is added and the browser lands once, so the flag would prevent no harm (`03` section 7). |
| S4 | ~~No redirect when already on `/login`.~~ **Dropped (user, 11.11.):** `PUBLIC_PATHS` stays in `currentUser.tsx`. | Nothing on `/login` calls `api()` (`CurrentUserProvider` skips it, `AuthBar` and the bell render nothing without a user), so no loop can happen (`03` section 7). Parked trigger: a backend call added to the sign-in page. |
| S5 | `next` is built from `window.location.pathname + search` and encoded; it is **not** checked in `api.ts`. | It is our own current path; the check belongs to the reader (`login/page.tsx` already uses `safeReturnTo`). `session.ts` is a server module and must not be imported into browser code. |
| S6 | `window.location.replace`, not `assign`. | The broken page should not be in the back history. Same as today in `currentUser.tsx`. |

**Accepted loss:** text typed into a form whose submit gets the 401 is lost. A 401 only happens when the refresh token is gone (the access token is refreshed silently by `proxyToBackend`), so after a week or after signing out elsewhere. Parked trigger: if users complain, keep drafts.

## 5. Acceptance criteria
1. `api()` on a 401 calls `window.location.replace("/login?next=<encoded path + query>")` and throws `ApiError(401, ...)`.
2. ~~Two 401s cause one `replace`.~~ Dropped with S3.
3. ~~On `/login` a 401 does not redirect.~~ Dropped with S4.
4. Other statuses (403, 404, 422, 0 network) do not redirect.
5. `CurrentUserProvider` no longer redirects itself; a 401 from `/me` still ends on `/login?next=...` (through `api()`).
6. `InboxBell` has no `stopped` ref; its `stopsPollingAfter401` test is replaced or removed.
7. `04_FRONTEND.md` line 35 stays true; add "(in `api()`, `lib/api.ts`)" so the next reader finds the place.

## 6. Tests (one rule, one level)
| Test | Level | File |
|---|---|---|
| `aRejectedSessionSendsTheVisitorToSignInAndBackToThisPage` (criterion 1) | unit | `lib/api.test.ts` |
| `otherErrorsDoNotNavigate` (4, parameterised: 403, 404, 422, 500) | unit | `lib/api.test.ts` |
| `aNetworkFailureDoesNotNavigate` (4, status 0) | unit | `lib/api.test.ts` |
| Change `sendsAVisitorWithoutASessionToSignInAndBackToTheSamePage` | component | `lib/currentUser.test.tsx` (line 42): `api` is mocked there, so the redirect is no longer this component's job; remove the test, keep `doesNotAskOnThePublicPage` |
| Remove `stopsPollingAfter401` | component | `components/InboxBell.test.tsx` (line 449) |

Leave unchanged: `Balances.test.tsx` line 58 and `GroupList.test.tsx` line 41 mock `api`, so they still show the banner; they stay valid as "an error is shown".

## 7. Commands and finish
```
cd frontend && npm install          # first time in this worktree
npm run lint && npm run typecheck && npm test && npm run build
```
Commit format: `fix(frontend): a 401 anywhere sends the visitor to sign in` (code, tests, the `04` line and the changed comments together: docs change with the code, CLAUDE.md); this spec in its own `docs(frontend): ...` commit before it. Then `/review-branch`; changes reach `main` by pull request with green CI (`docs/devsetup/README.md`).
