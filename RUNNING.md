# Running ShareWise (Ex4 slice)

Create a group, invite friends, record an equal-split expense, see who owes what.

## Prerequisites
- Java 21 (Maven is bundled as `./mvnw`)
- Node.js 22+ and npm
- Docker Desktop **running** (database and all backend tests need it)

## Start everything

```sh
# 1. Database (Postgres 17 on :5432)
docker compose up -d db

# 2. Backend on :8080 (dev profile = stub identity + four seeded users)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend on :3000 (second terminal)
cd frontend
npm install
cp .env.example .env.local      # BACKEND_URL=http://localhost:8080
npm run dev
```

Open http://localhost:3000. Pick a user in the header (Alice, Bob, Carol, David). The choice is stored in a
cookie that the Next.js `/api` routes forward to the backend as `X-User-Id`.
(Keycloak replaces this stub later; it only works with the `dev` profile.)

### Try the Mallorca example
1. As **Alice**: create the group "Mallorca 2027", open it, create an invite link.
2. As **Bob / Carol / David**: switch user, open the link, click "Join group".
3. As **Alice**: add "Hotel", 900,00 €, payer Alice, everyone. As **Bob**: "Dinner", 180,00 €, payer Bob, everyone.
4. Balances tab: Alice +630,00 €, Bob -90,00 €, Carol -270,00 €, David -270,00 €. Balances arrive through an event, so
   they can lag the expense by a moment.

## Tests

```sh
cd backend  && ./mvnw verify                                             # 127 tests, needs Docker
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
npx @redocly/cli lint docs/api/openapi.yaml                              # the API contract
```

`ModularityTests` fails the build if a module reaches into another module's internals.
`MallorcaSliceTest` runs the whole slice (all four modules, real Postgres) as one scenario.

## Frontend without a backend
```sh
sh docs/api/mock.sh                                   # Prism serves the examples of docs/api/openapi.yaml on :4010
BACKEND_URL=http://localhost:4010 npm run dev         # in frontend/
```

## Reset the database
`docker compose down -v` deletes the volume; Flyway recreates the schema on the next backend start.

## Where things are
- `docs/api/openapi.yaml` the API contract (change it first, then code)
- `docs/2026-10-24-sharewise-modulith-ex4-design.md` design decisions, `docs/...-ex4.md` the plan
- `backend/src/main/java/de/hsh/favs/sharewise/{account,group,expense,settlement,shared}` the modules
