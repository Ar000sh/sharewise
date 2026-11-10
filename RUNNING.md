# Running ShareWise (Ex4 slice)

Sign up, create a group, invite friends, record an equal-split expense, see who owes what. Accounts and passwords
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
- Sign-up happens on Keycloak's own registration page: username, first name, last name, email and password (at least
  8 characters). The username is 3 to 30 lowercase letters, digits, dots, underscores or hyphens (no `@`); it is what
  other people type to invite you, together with your email. You can sign in with the username or the email. There is
  no email verification locally.
- The ShareWise account is created on the first authenticated request after sign-in (display name and username from
  the token, see the design doc `docs/2026-10-30-keycloak-auth-design.md`). A person who signed up at Keycloak but
  never opened ShareWise has no account yet and cannot be invited.
- **After the username change (migration V106 and the changed realm) delete the old local data once:** `docker compose
  down -v`. The realm is imported again and the database starts empty; there is no backfill.
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
1. **Bob / Carol:** sign up first (choose a username). Only people with an account can be invited.
2. **Alice:** sign up, create the group "Mallorca 2027", open the Members tab, type Bob's username (or Carol's email)
   into **Username or email** and press **Invite**. "No account found" means that person has not signed in yet.
   Only an admin sees this form (the creator is the first admin); pick **Joins as** Admin to invite someone as an
   admin. Later, an admin can press **Make admin** next to a member; that member's bell shows "… made you an admin".
3. **Bob / Carol:** reload, open the bell (**Inbox**), read "Alice … invited you to Mallorca 2027" and press **Accept**
   (or **Decline**). Alice's bell shows "… joined Mallorca 2027" a moment later (it arrives through an event).
4. **Alice:** add "Hotel", 900 €, paid by Alice, everyone. **Bob:** "Dinner", 180 €, paid by Bob, everyone.
5. Balances tab: Alice +540,00 €, Bob -180,00 €, Carol -360,00 €. They arrive through an event, so they can lag the
   expense by a moment.

## Tests

```sh
cd backend  && ./mvnw verify                                             # needs Docker (Postgres and Keycloak containers)
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
npx @redocly/cli lint docs/api/openapi.yaml                              # the API contract
```

`ModularityTests` fails the build if a module reaches into another module's internals.
`MallorcaSliceTest` runs the whole slice (all four modules, real Postgres, real Keycloak) as one scenario. The Keycloak
test container takes about 75 s to start, once per test run.

### End-to-end test in a real browser (Playwright)
Three people register on Keycloak's registration page in three browsers, share a group and check the balances; a
second test covers a wrong password, sign-out and the redirect to sign-in.
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
- `backend/src/main/java/de/hsh/favs/sharewise/{account,group,expense,settlement,shared}` the modules
