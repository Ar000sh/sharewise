# ShareWise testing style

Draft by Track A, for the hub to move to `docs/TESTING_STYLE.md` (Track A may not create files under `docs/`). Applies to every backend track. Reference implementation: Track A, `backend/src/test/java/de/hsh/favs/sharewise/account` and `.../group`.

Rule of thumb: **few tests that each protect a rule or a risk.** Do not test getters, trivial mapping or the framework. Do not write the same case at two levels.

## Levels

| Level | Tools | Use it for | Track A example |
|---|---|---|---|
| Unit | plain JUnit, AssertJ, no Spring | domain rules in the aggregate | `GroupTest` |
| Service | JUnit + Mockito (`@ExtendWith(MockitoExtension.class)`), mocked repositories and ports | orchestration: what is loaded, saved, published, in which order; that a rejected call stores and publishes nothing | `GroupServiceTest`, `InvitationServiceTest` |
| API contract | `@ModuleIntegrationTest` + MockMvc, real Postgres | status codes, JSON shape, problem+json `code`, 401/403/404, events recorded with `ApplicationEvents` | `GroupControllerTest`, `InvitationControllerTest` |
| Database-dependent behaviour | same annotation, call the bean directly | anything a mocked repository cannot prove: lazy loading, locks, queries, constraints | `GroupMembershipImplTest` |

A mocked repository only confirms what the test taught it. If the risk is in the database interaction, use the real database.

## Test layout

- Mirror the main package (`group.GroupTest` tests `group.Group`). Package-private classes are tested from the same package.
- Name tests by behaviour: `acceptAfterSevenDaysIs410AndNobodyJoins`, not `testAccept2`.
- Body in three blocks marked `// given`, `// when`, `// then` (leave a block out when it is empty). One reason to fail per test; parameterise with `@ParameterizedTest` when only the input differs.
- Assert what was **not** done as well: no event published, nothing saved, nobody joined.

## Fixtures

- One class per domain type, named `<Class>Fixture` (`UserFixture`, `GroupFixture`, `InvitationFixture`), in the test package of that type.
- A fixture holds data with **sensible defaults**, `withX(...)` methods that return `this`, and a `build()` that goes through the real factory or constructor so the result is always valid.
- Tests create what they need: `new GroupFixture().withCreator(bob.id()).withMember(carol.id()).build()`. Override only what the test is about.
- Seeded dev users are available as `UserFixture.alice()/bob()/carol()/david()`.
- HTTP set-up helpers for controller tests live in a small class (`GroupRequests`), not in the tests.
- Do not call test doubles "fake". Use mocks (Mockito) or a fixture; avoid hand-written in-memory implementations unless a mock truly cannot express the case.

## Shared integration set-up

`@ModuleIntegrationTest` (Track A: `group/ModuleIntegrationTest`) is a composed annotation: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("dev")`, `@RecordApplicationEvents`, and imports `TestcontainersConfiguration` and a clock config. Every integration test class uses the same annotation, so they share **one** Spring context and one Postgres container.

- Time: inject `java.time.Clock`; tests replace it with a `@Primary` `MutableClock` (`reset()` in `@BeforeEach`, `advance(Duration)`). Never sleep.
- The database is shared by all integration tests of a run. Do not assume it is empty and do not rely on row order: create data with unique names (`"Joined " + UUID.randomUUID()`) and assert on your own rows. With a frozen clock, rows created in the same instant tie on `createdAt`.
- Each track may copy the annotation and the clock classes into its own test package; do not import another track's test classes.

## Pitfalls we hit (read before writing service code)

1. **Lazy loading outside a transaction.** Beans in a module's `api` (for example `GroupMembership`) are called by other modules **without** a transaction. A JPA collection read there throws `LazyInitializationException`. Answer such calls with a repository query that returns a plain value (`existsMember(...)`), or return a snapshot built inside a `@Transactional` method. Do not return entities.
2. **Default interface methods bypass the proxy.** `GroupMembership.requireMember` is a `default` method; `@Transactional` on the implementing class does not wrap it, and its calls to `this.isMember(...)` are not proxied either. Anything such a method calls must work with no transaction.
3. **Request-scoped state outside a request.** Code run from an event listener thread has no HTTP request. `RequestContextHolder.currentRequestAttributes()` throws there; use `getRequestAttributes()` and handle `null`.
4. **Stale compiled tests.** After deleting or renaming a test class, run `./mvnw clean ...`; old `.class` files in `target/` keep running and fail.
5. A test written against the integration database found 1 and 3 above; mocked tests could not have. Add one integration test for every cross-module `api` bean.

## Commands

```
cd backend
./mvnw -q verify                                   # everything, Docker Desktop must run
./mvnw -q test -Dtest='GroupTest,GroupServiceTest' # several classes: comma separated, quoted

# coverage without changing the pom (report in target/site/jacoco/index.html)
./mvnw -q clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent verify org.jacoco:jacoco-maven-plugin:0.8.13:report
```

Coverage is a check for blind spots, not a goal: look at what is uncovered and decide whether it matters. Track A ended at 94 to 100 percent per class, the gaps being `orElseThrow` lambdas.
