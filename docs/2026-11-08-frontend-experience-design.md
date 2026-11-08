# Frontend experience and sign-in clarity

Date: 08.11.2026. Scope: the existing Next.js application, developed locally.

The consolidated [progress and handoff](2026-11-08-progress-and-handoff.md) records the implementation entry points,
requirements review, working preferences and durable screenshot copies. [CI/CD special cases](2026-11-08-ci-cd-special-cases.md)
are tracked separately from the completed UI work.

## Decisions

- Keep Keycloak accounts and the existing OIDC authorization code flow with PKCE (user).
- Explain the handoff before leaving ShareWise: an existing username or email works on the sign-in page; registration is a separate action.
- Explain cancelled and expired callbacks, and retain the safe return path when trying again.
- Keep credentials on Keycloak's page. OAuth 2.0 is already the authorization protocol; OIDC supplies the login identity.
- Use a restrained neutral palette, professional alignment, spacing and typography, and readable light and dark themes (user). Shared surface/button/field tokens keep the screens consistent.
- Carry that style through groups, expenses, balances, members and the invitation inbox. Do not change financial rules, API shapes or identity-provider configuration.

## Implementation

Global CSS owns visual tokens and common controls. Existing components retain their data-loading and submission paths. Small SVG icons and member avatars use local code without a new dependency. Form labels and accessible action names stay recognizable.

The login callback redirects failed attempts to an allowlisted reason on `/login`. That page renders a fixed explanation, never the provider's raw error text. The existing `safeReturnTo` check remains the boundary for retry destinations.

## Validation

Run frontend lint, type checking, existing unit/component/route tests, and a production build. Add behavior checks for sign-in explanations, retry destinations and keyboard tab navigation. Inspect representative screens at desktop and phone widths in light and dark mode; use browser API fixtures only for visual inspection when the local backend is unavailable. A real Keycloak round trip is a separate integration check and must be reported as such.

### Results

- ESLint, TypeScript and the production build passed.
- All 157 frontend tests passed with `node node_modules/vitest/vitest.mjs run --pool=threads --maxWorkers=2`. A retry with the default fork pool encountered a Windows worker-start timeout; the thread pool completed normally.
- Browser fixtures exercised groups, expenses, balances, members and inbox screens at 1440 px and 390 px in both colour schemes. Login, expenses and inbox had no horizontal overflow; no page errors were reported. Screenshots were inspected from the production preview, because the existing development server retained an older compiled stylesheet.
- The real sign-in start returned 302 to Keycloak with `response_type=code` and `code_challenge_method=S256`; Keycloak returned 200 with its login form. No credentials were entered, so a complete account login/logout journey was not rerun.
- No dependency was added. The pre-existing `frontend/package-lock.json` edit was preserved.
