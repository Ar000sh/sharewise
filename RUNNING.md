# Running ShareWise (Ex4 slice)

Sign up, create a group, invite friends, record an equal-split expense, see who owes what. Real accounts: email,
name and password (stored as a BCrypt hash), and a session token in an httpOnly cookie.

## Prerequisites
- Java 21 (Maven is bundled as `./mvnw`)
- Node.js 22+ and npm
- Docker Desktop **running** (database, backend tests)

## Start everything

```sh
# 1. Database (Postgres 17 on :5432)
docker compose up -d db

# 2. Backend on :8080 (~30 s)
cd backend
./mvnw spring-boot:run

# 3. Frontend on :3000 (second terminal)
cd frontend
npm install
cp .env.example .env.local      # BACKEND_URL=http://localhost:8080
npm run dev
```

Open http://localhost:3000. You land on the sign-in page; **Create an account** to sign up.

### Try the Mallorca example (needs one browser profile per person)
Use a normal window for Alice and private/incognito windows (or other browsers) for Bob and Carol, because the session
cookie is per browser.
1. **Alice:** sign up, create the group "Mallorca 2027", open the Members tab, **Invite someone**, copy the link.
2. **Bob / Carol:** open the link, **Create an account**, then **Join group**.
3. **Alice:** add "Hotel", 900 €, paid by Alice, everyone. **Bob:** "Dinner", 180 €, paid by Bob, everyone.
4. Balances tab: Alice +540,00 €, Bob -180,00 €, Carol -360,00 €. They arrive through an event, so they can lag the
   expense by a moment.

## Tests

```sh
cd backend  && ./mvnw verify                                             # 124 tests, needs Docker
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
npx @redocly/cli lint docs/api/openapi.yaml                              # the API contract
```

`ModularityTests` fails the build if a module reaches into another module's internals.
`MallorcaSliceTest` runs the whole slice (all four modules, real Postgres) as one scenario.

### End-to-end test in a real browser (Playwright)
Three people sign up in three browsers, share a group and check the balances; a second test covers a wrong password,
sign-out and the redirect to sign-in.
```sh
docker compose up -d db
(cd backend && ./mvnw spring-boot:run)          # leave running
cd frontend
npx playwright install chromium                 # once
npm run e2e                                     # starts the frontend itself on :3100
```
Every run signs up fresh users (unique emails), so it can be repeated against the same database.

## Frontend without a backend
```sh
sh docs/api/mock.sh                                   # Prism serves the examples of docs/api/openapi.yaml on :4010
BACKEND_URL=http://localhost:4010 npm run dev         # in frontend/
```
Sign in with any email and password on the mock; it answers with the example token.

## Reset the database
`docker compose down -v` deletes the volume (all accounts too); Flyway recreates the schema on the next backend start.

## Where things are
- `docs/api/openapi.yaml` the API contract (change it first, then code)
- `docs/2026-10-24-sharewise-modulith-ex4-design.md` design decisions, `docs/...-ex4.md` the plan
- `backend/src/main/java/de/hsh/favs/sharewise/{account,group,expense,settlement,shared}` the modules
