# 02 Testing

Rule of thumb: **few tests that each protect a rule or a risk.** Do not test getters, trivial mapping or the framework. Never write the same case at two levels. This document replaces the earlier `TESTING_STYLE.md` draft.

## 1. Levels

| Level | Tools | Use it for | Where |
|---|---|---|---|
| **Domain unit** | plain JUnit 5 + AssertJ, no Spring | every business rule of an aggregate or value object (R1 to R17), including the rejected cases | `<module>/domain/` |
| **Service** | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), mocked repositories and ports | orchestration only: what is loaded, saved, published and in which order; that a rejected call saves and publishes nothing | `<module>/application/` |
| **API contract** | `@ModuleIntegrationTest` + MockMvc, real Postgres | status codes, JSON shape (field names exactly as the contract), problem `code`s, 401/403/404, events recorded with `ApplicationEvents` | `<module>/web/` |
| **Database behaviour** | same annotation, call the bean directly | what a mock cannot prove: lazy loading, locks, queries, constraints, `api` port implementations called with no transaction | `<module>/application/` or `persistence/` |
| **Architecture** | Spring Modulith `ApplicationModules.verify()`, ArchUnit | module boundaries, layering, naming (document 01) | `ModularityTests`, `ConventionTests` |
| **Slice** | one `@SpringBootTest`, all real beans | the Mallorca scenario across modules, including the event-driven balances | `MallorcaSliceTest` |
| **End to end** | Playwright, real browser, real backend | sign up, join through an invite, record expenses, see balances | `frontend/e2e/` |

A mocked repository only confirms what the test taught it. If the risk is in the database interaction, use the real database.

Where a rule lives decides where it is tested: a rule in an aggregate is tested in the aggregate's unit test, once. The controller test checks that the rule's failure turns into the right status and `code`, not the rule again.

## 2. Names and layout

- Test classes mirror the main package: `expense.domain.ExpenseTest` tests `expense.domain.Expense`. Package-private classes are tested from the same package. Suffix `Test`; integration tests that need Docker are still `*Test` (one Maven run, no `IT` split).
- **Test methods are sentences about behaviour**, lower camel case, no `test` prefix, no numbers: `acceptAfterSevenDaysIs410AndNobodyJoins`, `equalSplitGivesTheRemainderToTheFirstParticipants`. The name says the situation and the outcome.
- Body in blocks marked `// given`, `// when`, `// then` (leave a block out when it is empty; `// when / then` is allowed when they are one expression). One reason to fail per test; use `@ParameterizedTest` when only the input differs.
- Assert what was **not** done too: no event published, nothing saved, nobody joined.
- Test vocabulary equals production vocabulary (`paidBy`, `totalAmount`, `splitStrategy`). A test that says `payer` is a rename that was missed.

## 3. Fixtures

- One class per domain type, named `<Class>Fixture` (`AccountFixture`, `GroupFixture`, `ExpenseFixture`), in the test package of that type.
- Defaults that are valid; `withX(...)` methods that return `this`; a `build()` that goes through the real factory or operation so the result is always valid.
- Create what the test needs and override only what it is about: `new GroupFixture().withCreator(bob).withMember(carol).build()`.
- HTTP set-up for controller tests lives in a small class per module (`GroupRequests`, `ExpenseRequests`), not inside the tests.
- **No seeded users, no dev profile, no shortcuts around authentication.** Integration tests that need a caller sign up a real account through `POST /auth/register` (`AccountFixture.signUp(mvc, json, "Alice")` returns id and token) and send `Authorization: Bearer <token>`. Unit and service tests use `AccountFixture` values with fixed ids in memory.
- Do not call test doubles "fake". Use Mockito mocks or a fixture. Hand-written in-memory implementations only when a mock truly cannot express the case, and then name them `Stub<Port>`.

## 4. Shared integration set-up

`@ModuleIntegrationTest` is a composed annotation: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@RecordApplicationEvents`, plus the imports of `TestcontainersConfiguration` (Postgres 17) and a clock configuration. Every integration test uses it, so they share **one** Spring context and **one** container.

- **Time:** inject `java.time.Clock`; tests replace it with a `@Primary` `MutableClock` (`reset()` in `@BeforeEach`, `advance(Duration)`). Never `Thread.sleep`.
- **Eventual consistency:** balances arrive through an asynchronous event. Use Awaitility (`await().atMost(Duration.ofSeconds(10)).untilAsserted(...)`) and assert the final state. Never sleep, never retry by hand.
- **Shared database:** all integration tests of a run use the same database. Do not assume it is empty and do not rely on row order. Create data with unique names or emails and assert on your own rows. With a frozen clock rows created in the same instant tie on their timestamp.

## 5. Pitfalls we hit (read before writing service code)

1. **Lazy loading outside a transaction.** `api` ports are called by other modules *without* a transaction. Reading a JPA collection there throws `LazyInitializationException`. Answer with a repository query that returns a plain value, or a snapshot built inside a `@Transactional` method. Never return entities.
2. **Default interface methods bypass the proxy.** `GroupMembership.requireMember` is a `default` method; `@Transactional` on the implementation does not wrap it, and its calls to `this.isMember(...)` are not proxied. Anything such a method calls must work without a transaction.
3. **Request-scoped state outside a request.** Code run from an event listener has no HTTP request: `RequestContextHolder.currentRequestAttributes()` throws there.
4. **Stale compiled tests.** After deleting or renaming a test class run `./mvnw clean ...`; old `.class` files in `target/` keep running.
5. Mocked tests cannot find 1 and 3. Add one integration test for every cross-module `api` port implementation.
6. Do not assert an order the code does not guarantee (members of a group created in the same instant).

## 6. What the architecture tests check

`ConventionTests` fails when: `domain` imports Spring, web or another layer of its module; a class in `api` is not an interface, record or enumeration; a `*Controller` lies outside `web`, a `*Repository` outside `persistence`, a `*Service` outside `application`; `application`, `persistence` or `web` types are used by another module; a class is called `MemberId`, `*Dto`, `*Manager`, `*Util` or `*Helper`; a domain event does not live in `api`. When you add a rule to document 01, add it to `ConventionTests` in the same commit.

## 7. Frontend tests
See document 04: Vitest + Testing Library beside the component, Playwright for the user journeys.

## 8. Commands

```
cd backend
./mvnw -q verify                                    # everything; Docker Desktop must run
./mvnw -q test -Dtest='GroupTest,GroupServiceTest'  # several classes: comma separated, quoted
./mvnw -q clean verify                              # after deleting or renaming tests

cd frontend
npm test                                            # unit and component tests
npm run e2e                                         # Playwright; backend + Postgres must run

# coverage without changing the pom (report in target/site/jacoco/index.html)
./mvnw -q clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent verify org.jacoco:jacoco-maven-plugin:0.8.13:report
```

Coverage is a check for blind spots, not a goal: look at what is uncovered and decide whether it matters.
