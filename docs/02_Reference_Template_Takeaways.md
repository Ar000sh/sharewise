# Prof's reference project (`favsdemo`): what we take, what we don't

**Source:** `favsdemo/` (prof's GitLab repo `hg-wise26/favsdemo`, cloned next to `sharewise/`). The prof says it is a template for the *layered* architecture and will explain it on Mon 26.10. Dockerfiles and pipeline can be ignored for now.
**Purpose of this file:** keep the high-level decisions, rebuild everything in our own code and names. 🟢 = our decision.

## 1. What the demo actually is

A hello-world skeleton, not a layered example: one controller, one record, no service, repository or entity. "Layered" is only the intended direction. What it really shows is the **wiring between the two apps**.

```
Browser ──► Next.js page (client component)
              │ fetch('/api/greetings')
              ▼
            Next.js route handler  (app/api/greetings/route.ts)   ← acts as BFF
              │ fetch(BACKEND_URL + '/greeting')
              ▼
            Spring Boot @RestController  ──► { "message": "Hello, World!" }
```

## 2. High-level decisions in the demo

| Area | Demo does | Our take |
|---|---|---|
| Repo layout | Monorepo: `backend/<app>/`, `frontend/`, `docker-compose.yml` and `.gitlab-ci.yml` at the root | **Adopt.** We already have `sharewise/backend` and `sharewise/frontend` |
| Backend build | Spring Boot parent, Maven + wrapper (`mvnw`), Java 17, JAR with embedded server | **Adopt** (slides say the same). Check which Boot version Initializr gives us; the demo uses 4.1.1, the slides 4.0.6 |
| Backend deps | `webmvc`, `data-jpa`, H2 (+ `h2console`), Lombok, test starters | **Adopt as base.** 🟢 Add validation; plan a real DB before Ex8 (H2 in-memory loses data) |
| Backend structure | Single flat package, controller next to the application class | **Deviate.** One top-level package per bounded context (account, group, expense, settlement) plus a shared-types package. Inside: hexagonal for core, layered for supporting (P1 §4.2) |
| API DTOs | Java `record` for the response | **Adopt.** Records for request/response DTOs, never expose domain objects |
| Browser → backend | Browser **never** calls Spring directly; only `/api/...` on Next.js, which forwards | **Adopt.** Matches our gateway decision (requirements §3b): no CORS, one place to change the target later |
| Backend address | `BACKEND_URL` env var, default `http://localhost:8080` | **Adopt.** Needed for Ex6 (compose uses `http://<service>:8080`) |
| Frontend routing | App Router: `app/page.tsx`, `app/<feature>/page.tsx`, `app/api/<x>/route.ts` | **Adopt.** One route folder per feature (groups, expenses, balances) |
| Data fetching | Client component with `useEffect` + loading/error state | **Partly.** Fine for interactive pages; prefer server components for read-only pages (slides 33–35) |
| Route handler | `await connection()` (dynamic), `cache: 'no-store'`, passes status through, 500 on exceptions | **Adopt the idea,** write one small shared helper for the proxy instead of copy-pasting per route |
| Styling | Tailwind 4 | Optional (slides). Decide when we build the UI |
| Containers / CI | Backend Dockerfile copies the prebuilt JAR, frontend is multi-stage; compose pulls images from the GitLab registry with `BACKEND_URL` set; CI has `build` → `package` stages, pushes `latest` + short SHA on the default branch | **Later** (Ex6/7). Keep the pattern in mind: build once in CI, package, push, compose by service name |

## 3. Things in the demo we should not copy

- `next.config.ts` has `experimental.agentFeedback`, `cacheComponents`, `partialPrefetching` and a Turbopack Tailwind rule. Only enable what we understand.
- The generated Next.js landing page and `frontend/AGENTS.md` (written by `next dev`) are noise.
- Weak spots: `useState(null)` without a type, a German error text mixed with English, only a `contextLoads` test, no config besides the app name.
- The Java package `de.hsh.i.favs.favsdemobe` and names like `Greeting*` are the prof's. We use our own group id and package, e.g. `de.hsh...sharewise` (🟢 confirm), with context names from the P1 model.

## 4. Consequences for the Ex4 skeleton

1. Backend: one Spring Boot app, packages `account`, `group`, `expense`, `settlement`, `shared`. Each module exposes a small public API (interface + domain events); nothing reaches into another module's classes or tables.
2. Frontend: Next.js App Router, pages per feature, all backend calls through `app/api/...` route handlers that use `BACKEND_URL`.
3. First vertical slice (needed by 26.10): create group → add expense → see balances, end to end through the BFF.
4. Verify our module boundaries with an automated check (e.g. ArchUnit or Spring Modulith's verification), so P2 can show that the boundaries are real.

## 5. Proposal for the team (to agree before we start building)

| # | 🟢 Proposal | Why |
|---|---|---|
| T1 | Newest stable versions of everything (Spring Boot, Next.js, React, Tailwind), Java 21 or newer. Exact numbers taken from start.spring.io and `npm view` when we scaffold | Prof's slides and demo already disagree, so there is no fixed target |
| T2 | One top-level package per bounded context: `account`, `group`, `expense`, `settlement`, plus a `shared` package (Money, GroupId, MemberId) | Module = bounded context (P1) |
| T3 | **Spring Modulith** to enforce the boundaries: public API and events only, everything else package-private, `verify()` test in the build | A package per context alone is only folders; the build has to fail when a boundary is broken. Not specified by the prof |
| T4 | Hexagonal inside the core modules (expense, settlement), layered inside the supporting ones (group) and the generic one (account) | P1 §4.2 |
| T5 | Records for request/response DTOs; domain objects are never exposed | Demo does the same |
| T6 | Browser → Next.js `/api/...` → backend via `BACKEND_URL`; one shared proxy helper | Demo + requirements §3b |
| T7 | First vertical slice by 26.10: create group → add expense → see balances | Ex4 deadline |

If the team agrees, we start with T7 on a scaffold built from T1–T6.

## 6. Open points to ask in the exercise class (26.10)

- Is the demo's flat package an oversimplification, or is the intended layering by *technical* layers (`controller/service/repository`) rather than by context? We chose by context.
- Which Boot and Java version should we use (demo: 4.1.1 / 17, slides: 4.0.6)?
- Is Next.js as the single entry point (BFF/gateway) what is intended for the later scaling steps?
