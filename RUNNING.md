# Running ShareWise

Sign up, create a group, invite friends, record an equal-split expense, see who owes what, settle up (report a payment,
the creditor confirms or rejects it, or forgives a debt). Accounts and passwords
belong to Keycloak (the identity provider); ShareWise never sees a password. The Next.js server keeps the tokens in an
httpOnly cookie.

## Prerequisites
- Java 21 (Maven is bundled as `./mvnw`)
- Node.js 22+ and npm
- Docker Desktop **running** (database, Keycloak, backend tests)
- Once per clone, before the first commit: `git config core.hooksPath .githooks` (commit and push gates; see
  `docs/devsetup/README.md`)

## Start everything

```sh
# 1. Database (Postgres 17 on :5432) and Keycloak (on :8180)
docker compose up -d db keycloak
#    Keycloak needs a moment. Wait until this URL answers with JSON:
#    http://localhost:8180/realms/sharewise/.well-known/openid-configuration

# 2. Backend on :8080 (~30 s)
cd backend
./mvnw spring-boot:run

# 3. Frontend on :3000 (second terminal)
cd frontend
npm install
cp .env.example .env.local      # the defaults match the compose file
npm run dev
```

Open http://localhost:3000. You land on the sign-in page; **Create an account** opens Keycloak's registration page.
If port 5432 is taken by another local Postgres, stop that one first (the compose file maps the fixed port).

### What the three Keycloak variables in `frontend/.env.example` are
- `KEYCLOAK_ISSUER` the realm URL (`http://localhost:8180/realms/sharewise`). The browser, the Next.js server and the
  backend (`application.yml`) must all see the same value.
- `KEYCLOAK_CLIENT_ID` the public client of the frontend (`sharewise-web`, code flow with PKCE).
- `APP_URL` the public address of the frontend (`http://localhost:3000`); redirects after sign-in and sign-out are built
  from it, and the realm file must list it as a redirect URI.

### Signing up and in
- Sign-up happens on Keycloak's own registration page: first name, last name, email and password (at least 8
  characters). There is no email verification locally.
- The ShareWise account is created on the first authenticated request after sign-in (display name from the token, see
  the design doc `docs/2026-10-30-keycloak-auth-design.md`).
- **Sign out** ends the Keycloak session too and shows a Keycloak confirmation page ("Logout"); confirm it to come
  back.
- Admin console: http://localhost:8180 with `admin` / `admin`. These credentials are in `docker-compose.yml` and are
  for local development only.

### Changing or wiping Keycloak
- Keycloak keeps its data (users and realm) in the named volume `sharewise-keycloak`. Users survive a restart and
  `docker compose down`.
- The realm file `docker/keycloak/sharewise-realm.json` is imported only while that volume has no realm. After you
  change the file, run `docker compose down -v` and start again; recreating the container is not enough.
- `down -v` wipes Keycloak and Postgres **together**, on purpose. If only Keycloak were wiped, a user who signs up
  again with an email that still exists in ShareWise would get a new Keycloak id, and the backend would answer 401.
- Running this branch's backend once applies migration V105, which deletes all local data (accounts, groups,
  expenses) and drops the password columns. Afterwards the backend of `main` no longer starts on that database;
  `docker compose down -v` restores it.
- The e2e tests register a fresh user per run, so test users accumulate in the local realm. `docker compose down -v`
  clears them.

### Try the Mallorca example (needs one browser profile per person)
Use a normal window for Alice and private/incognito windows (or other browsers) for Bob and Carol, because the session
cookie is per browser.
1. **Alice:** sign up, create the group "Mallorca 2027", open the Members tab, **Invite someone**, copy the link.
2. **Bob / Carol:** open the link, **Create an account**, then **Join group**.
3. **Alice:** add "Hotel", 900 €, paid by Alice, everyone. **Bob:** "Dinner", 180 €, paid by Bob, everyone.
4. Balances tab: Alice +540,00 €, Bob -180,00 €, Carol -360,00 €. They arrive through an event, so they can lag the
   expense by a moment.
5. **Carol:** Settle up tab, "You owe" shows 360,00 € to Alice; **I paid**, **Send payment**. The row now says
   "360,00 € awaiting confirmation"; no balance has changed yet.
6. **Alice:** the Settle up tab label and the home page show a badge. In the Settle up tab the box "Waiting for your
   answer" (above the plan) lists Carol's payment: **Confirm** (or **Reject** with a reason). After a moment the plan
   shows only "Bob Tester owes 180,00 €" and the Balances tab Alice +180,00 €, Bob -180,00 €, Carol 0,00 €.
7. **Alice:** **Waive** on Bob's row fills the "Waive manually" form below the plan; give a reason and **Waive debt**.
   Bob's debt is forgiven at once, no confirmation needed, and the plan says "Everybody is settled."

## Tests

```sh
cd backend  && ./mvnw verify                                             # needs Docker (Postgres and Keycloak containers)
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
npx @redocly/cli lint docs/api/openapi.yaml                              # the API contract
```

`ModularityTests` fails the build if a module reaches into another module's internals.
`MallorcaSliceTest` runs the whole slice (all four modules, real Postgres, real Keycloak) as one scenario: the expenses,
then a confirmed payment, a rejected one and a waiver. The Keycloak test container takes about 75 s to start, once per
test run.

### End-to-end test in a real browser (Playwright)
Three people register on Keycloak's registration page in three browsers, share a group, check the balances and settle
up (Carol reports a payment, Alice confirms it in her inbox, the plan shrinks to Bob's debt); a second test covers a
wrong password, sign-out and the redirect to sign-in.
```sh
docker compose up -d db keycloak                # the realm allows http://localhost:3100, the port of the e2e frontend
(cd backend && ./mvnw spring-boot:run)          # leave running
cd frontend
npx playwright install chromium                 # once
npm run e2e                                     # starts the frontend itself on :3100
```
Every run registers fresh users (unique emails), so it can be repeated against the same database.

## Frontend without a backend
The Prism mock serves the examples of the contract, which has no sign-in endpoints any more, so the sign-in redirect to
Keycloak still needs a running Keycloak. The mock is only useful for looking at pages with a fixed answer:
```sh
sh docs/api/mock.sh                                   # Prism serves the examples of docs/api/openapi.yaml on :4010
BACKEND_URL=http://localhost:4010 npm run dev         # in frontend/
```

## Reset the database
`docker compose down -v` deletes the volumes of Postgres and Keycloak (all accounts and users). Flyway recreates the
schema on the next backend start and Keycloak imports the realm file again. Plain `docker compose down` keeps both
volumes.

## Where things are
- `docs/api/openapi.yaml` the API contract (change it first, then code)
- `docs/2026-10-24-sharewise-modulith-ex4-design.md` design decisions, `docs/...-ex4.md` the plan
- `docs/2026-10-30-keycloak-auth-design.md` sign-in with Keycloak (decisions, failure modes)
- `docs/2026-11-02-settlements-design.md` settling up (plan, payments, waivers, balance status events)
- `docs/diagram-deltas.md` where the code differs from the domain diagram, and why
- `backend/src/main/java/de/hsh/favs/sharewise/{account,group,expense,settlement,shared}` the modules
