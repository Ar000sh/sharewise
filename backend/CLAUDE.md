# Backend (Spring Boot modulith)

Java 21, Spring Boot 4.1, Spring Modulith 2.1, JPA + Flyway on Postgres 17. Maven only through `./mvnw` (Maven is not on the PATH). Docker Desktop must run for every test that touches the database.

## Before you change code
- Find the module and the layer first (`account`, `group`, `expense`, `settlement`, `shared`; each with `api/ domain/ application/ persistence/ web/`). Other modules may only import a module's `api`.
- A new operation on an aggregate: use `/backend:domain-operation`. A changed endpoint: `/contract-change` (contract first).
- Read the pitfalls in `docs/conventions/02_TESTING.md` section 5 before writing service code or an `api` port (lazy loading outside a transaction, default methods bypassing the proxy, no request outside a request).
- A new module dependency is a design decision: change `package-info.java`, say why in the commit, and ask first (see "Who decides what" in the root CLAUDE.md).

## Commands (in `backend/`)
```
./mvnw -q verify                                    # everything, incl. ModularityTests and ConventionTests
./mvnw -q test -Dtest='GroupTest,GroupServiceTest'  # selected classes
./mvnw spring-javaformat:apply                      # format; the commit gate runs `validate`
./mvnw -q clean verify                              # after deleting or renaming tests (stale classes in target/)
```

## The rules (binding, checked by ArchUnit and Spring Modulith where possible)
@../docs/conventions/01_ARCHITECTURE_AND_NAMING.md
@../docs/conventions/03_CODE_STYLE.md
