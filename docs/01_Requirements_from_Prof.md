# ShareWise Implementation: Requirements from the Prof (P2 track)

**Purpose:** everything the lecture materials actually demand for the modulith (Ex4/5) and Presentation 2, in one place. Our own choices are kept apart in §5 and marked 🟢.
**Sources:** `01.Intro` slides 5, 7, 31–34 · `03.TechStack` slides 1, 2, 10, 23, 47, 50–66 (via `notes/skripte/`) · the prof's demo repo `favsdemo` (template, announced on Moodle; analysed in `02_Reference_Template_Takeaways.md`).
**Convention:** "(slide N)" = PDF page. "(note:)" = not from the source.

---

## 1. Deadlines

| What | Date | Source |
|---|---|---|
| **Ex4** Implementation Modulith (backend, frontend), **AI possible** | due **26.10.26** | Intro slide 7 |
| **Ex5** Implementation Modulith (cont.), AI possible | due 02.11.26 | Intro slide 7 |
| Ex6 Create containers and execute locally | 09.11.26 | Intro slide 7 |
| Ex7 Create pipelines (to build) | 16.11.26 | Intro slide 7 |
| **P2** "monolith implementation, limitations and change drivers (more users, more scaling, …)" | **17.11.26** | Intro slide 7 |
| Ex8 Deploy to eduDSCloud / Proxmox (adapt pipeline) | 23.11.26 | Intro slide 7 |
| Ex9–11 Factor out services, messaging, patterns | 30.11 / 07.12 / 14.12 | Intro slide 7 |

(note:) Today is 22.10. Ex4 is due in 4 days. The prof explains the demo in the exercise class on Mon 26.10.

### What must be ready by each date (our inference from the slide-7 table)

| By | Exercise says | So the project must have | Implication for design now |
|---|---|---|---|
| **26.10** | Modulith (backend, frontend) | Runnable backend + frontend, first vertical slice working end to end | Module skeleton (4 contexts) + at least one real flow |
| **02.11** | Modulith (cont.) | Feature set finished enough to show; all contexts present | Cross-module flows via events/interfaces, not shared tables |
| **09.11** | Create containers, run locally | One container per app (backend, frontend), started locally | Config via env vars (backend URL, DB), no hard-coded localhost |
| **16.11** | Pipelines (to build) | Automated build + tests: `mvn package`, `npm run build` | Tests exist and run headless; builds are reproducible |
| **17.11 (P2)** | Presentation: implementation, limits, change drivers | A working monolith to demo + evidence for limits/drivers | Note limitations while building (coupling, one DB, one deploy unit) |
| **23.11** | Deploy to eduDSCloud/Proxmox (adapt pipeline) | Deployable artifacts, persistent DB (H2 in-memory loses data) | Plan a real DB before this date |
| **30.11** | Factor out services, talk via messages | A module cut cleanly enough to extract | Pick the extraction candidate at P2 (likely Settlement or Account) |
| 07.12 / 14.12 | Implement patterns (outbox, saga, gateway, resilience) | Services + broker | – |
| 15.12 (P3) / 04.01 | Microservices + patterns / rework | – | – |

**Takeaway:** Ex4 and Ex5 are the build window (11 days). Everything from Ex6 on only packages, builds and deploys it. P2 comes one day after the Ex7 pipeline deadline, so it should be demoed from the containerised, pipeline-built version.

## 2. What is explicitly required

1. **A modular monolith with a backend and a frontend** (slide 7, Ex4/5). This is the whole spec for the implementation.
2. **Stack: Next.js + Spring Boot** (03.TechStack slide 1, Intro slide 7).
3. **AI assistance is allowed** for the modulith (slide 7).
4. P2 must cover three things: the **implementation**, its **limitations**, and the **change drivers** (more users, more scaling, …) (slide 7).
5. Learning goal behind it: *design and implement a modular monolith*, later *evaluate reasons for and against service extraction* (Intro slide 5).
6. The project path (Intro slide 34): … → **2 modular monolith → 3 containerize → 4 automate build/deploy → 5 identify change drivers** (= P2) → 6 extract services …

## 3. Stack details the prof's slides show

| Layer | What the slides show |
|---|---|
| **Frontend** | Next.js, App Router, `create-next-app@latest`, TypeScript Yes, ESLint Yes, `src/` Yes, Tailwind optional (slide 23, 26–27). Dev server on `localhost:3000`. SSR default, `"use client"` for state/hooks (slides 33–35). Fetches **JSON via REST** (slide 2, 19). Next.js API routes can act as a BFF (slide 10) |
| **Backend** | **Spring Boot 4.0.6**, starter-parent, **Maven**, `spring-boot-starter-webmvc`, **Lombok** (slide 54). Spring Initializr: IntelliJ or start.spring.io (slide 51). **Demo repo uses Boot 4.1.1**, Java 17, plus `data-jpa`, H2 + `h2console`, test starters. Slides and demo differ, so the version is our choice (see `02`) |
| **Persistence** | **Spring Data JPA** + **H2 in-memory** in the REST example (slide 58–59) |
| **REST style** | `@RestController`, `@Get/Post/DeleteMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody` (slides 60–66). **Constructor injection** preferred (slide 60). Stereotypes `@Component/@Repository/@Service/@Controller` (slide 50) |
| **Packaging** | Maven `package` → runnable JAR with embedded web server (slides 52) |
| **Java** | Version not stated. (note:) Spring Boot 4 needs Java 17+ |

**Hinted for later steps, not needed for Ex4/5:** containers (Ex6), CI pipelines (Ex7), eduDSCloud/Proxmox (Ex8), Azure, RabbitMQ (Intro slide 7).

## 3b. Gateway / routing (heard in class, NOT on the slides)

- Slides only say: Next.js API routes can be a **BFF** (03.TechStack slide 10). The lecture-11 row on Intro slide 7 lists "API-Gateway" (08.12, P3 material).
- ✅ **Confirmed by the prof's demo repo:** the browser calls only `/api/...` on Next.js (route handler, `app/api/greetings/route.ts`), which forwards to the backend at the env var `BACKEND_URL` (default `http://localhost:8080`; in compose `http://favsdemobe:8080`).
- 🟡 Still recalled from the lecture, unverified: the prof mentioned using **Next.js routing as the gateway** to route to several service instances once we scale. The demo only shows one backend. Confirm in class.
- 🟢 Design consequence for now: the browser calls only `/api/...` on Next.js, which forwards to a **configurable backend URL**. One instance now, several or a real gateway later. (note: Next.js has no built-in load balancing across instances.)

## 4. What is NOT specified (ask or decide)

- **Presentation format, length, team size**, how P2 and the implementation are graded.
- How Ex4/5 are **submitted** (repo link? demo? zip?).
- **Which features** the modulith must implement. Slides give only the journeys; no minimum scope.
- **What "modular" must look like in code**: package-per-context, a framework like Spring Modulith, or separate Maven modules. The slides say nothing about this.
- **Database** beyond the H2 example, **authentication** approach.
- ~~Whether the frontend must be separate from the backend~~ → **answered by the demo repo:** two separate apps in one repo (`backend/`, `frontend/`), Next.js route handlers as a thin proxy.
- **Package structure of the demo:** it is one flat package with a single controller, so it shows nothing about layering or modules. Is the intended layering technical (`controller/service/repository`) or by context? We chose by context (§5).
- **`src/` folder:** slides say `src/` Yes (slide 23), the demo has `app/` at the root.
- **Boot/Java version:** slides 4.0.6, demo 4.1.1 / Java 17.

## 5. Our inputs and constraints (🟢 = our own decision, from P1)

- **Modules = bounded contexts** from the P1 slides: **Account** (generic), **Group** (supporting), **Expense** (core), **Settlement** (core). 🟢
  - **Shared types** (Money, GroupId, MemberId) is **not a context**, only a code-organisation package. On the P1 context map it is the Shared Kernel between Expense and Settlement.
  - Context map edges: Account → Group (OHS/PL → ACL). Group → Expense and Group → Settlement (OHS/PL → Conformist). Expense ↔ Settlement: Partnership + Shared Kernel.
  - Sources: `work/ShareWise — Context Map, Ubiquitous Language & Domain Model (1).pdf`, `work/ShareWise_Domain_Model.drawio`, `work/ShareWise_Requirements.md`.
- **Hexagonal inside core contexts**, layered for supporting ones (P1 §4.2). 🟢
- **Modules talk only via public interfaces and domain events**, never via each other's tables. This is what makes service extraction (Ex9) possible and gives P2 its "limitations and change drivers" story. 🟢
- **Single source of truth for rules:** `work/ShareWise_Requirements.md` (R1–R17, journeys J1–J17).
- **Folders:** `sharewise/frontend` (Next.js) and `sharewise/backend` (Spring Boot), separate apps. 🟢 (assumption, see §4)

## 6. What P2 needs from the implementation (so we build with it in mind)

(note: our inference from the slide-7 title, not prof-specified)
- **Limitations** to be able to demonstrate or argue: one deployable, one DB, one scaling unit, shared release cycle, in-process coupling.
- **Change drivers** to be able to analyse per module: more users, more scaling, different load per context (e.g. Settlement calculations vs Expense writes), team growth, release frequency.
- Therefore: keep module boundaries **clean and checkable** and the implementation **measurable** (e.g. simple load test), so claims in P2 are backed by something.
