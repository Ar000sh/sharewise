# 01 Architecture and naming

## 1. Modules

One module per bounded context, plus `shared`. Root package `de.hsh.favs.sharewise`.

| Module | Context | Subdomain | Aggregates |
|---|---|---|---|
| `account` | Account | generic | `Account` |
| `group` | Group | supporting | `Group` (with entities `Membership`, `Invitation`) |
| `expense` | Expense | core | `Expense` |
| `settlement` | Settlement | core | `GroupLedger` (with entity `LedgerEntry`), `SettlementPayment` |
| `notification` | Notification | generic | `Notification` (the inbox: things that happened to a person, fed by events of group and settlement; a leaf, nothing depends on it, so it has no `api` package) |
| `shared` | none | none | value objects used by several contexts only (see 2.4); the package `shared.events` holds the two upstream balance events and nothing else (see 5.1) |

A module's allowed dependencies are declared once, in its `package-info.java` (`@ApplicationModule(allowedDependencies = ...)`), and checked by `ModularityTests`. Current rules: `account → shared`; `group → account::api, shared`; `expense → account::api, group::api, shared`; `settlement → account::api, group::api, expense::api, shared`; `notification → account::api, expense::api, group::api, settlement::api, shared` (it consumes events of group, expense and settlement; `expense::api` was added for `ExpenseRecorded`, no cycle because `expense` does not depend on `notification`), and **nothing depends on `notification`** (checked by `ConventionTests.nothingDependsOnTheNotificationModule`). Why a module of its own: payment entries come from `settlement`, which already depends on `group`; entries stored inside `group` would force `group → settlement`, a cycle. Adding a dependency is a design decision: change the line, say why in the commit.

## 2. One layout for every module (no exceptions)

```
<module>/
  api/           the module's public surface, the only package other modules may import
  domain/        aggregates, entities, value objects, enumerations, domain services
  application/   use cases (services), commands, event listeners, implementations of the api ports
  persistence/   Spring Data repositories
  web/           controllers, request and response records
  package-info.java
```

### 2.1 What goes where

| Package | Contains | Must not contain |
|---|---|---|
| `api` | domain events this module publishes (the one exception: 5.1); ports (interfaces) other modules call; read-model records those ports return; enumerations that appear in them | entities, repositories, services, Spring beans |
| `domain` | the model exactly as in the diagram; business rules; `@Entity`/JPA annotations are allowed on aggregates, entities and embeddables (our one pragmatic deviation from textbook hexagonal) | `org.springframework.*`, `jakarta.servlet.*`, Spring Data, other modules' types except `shared` and other modules' `api` value types, I/O, `Instant.now()` (take the time as a parameter) |
| `application` | `<Aggregate>Service` (transactions, loading, saving, publishing events), command records, `<Aggregate>Listener` classes that hand events to a service, `<Port>Impl` classes that implement this module's `api` ports | HTTP types, JSON, entities returned to other modules |
| `persistence` | `<Aggregate>Repository extends JpaRepository`, custom queries | business rules |
| `web` | `<Aggregate>Controller`, `<Operation>Request`, `<Thing>Response` records, mapping to and from application types | business rules, repositories, direct use of entities in JSON |

Dependency direction inside a module: `web → application → domain` and `application → persistence → domain`. `domain` depends on nothing else in the module. Nothing outside `<module>` may import `<module>.domain`, `.application`, `.persistence` or `.web`. All of this is checked by `ConventionTests`.

### 2.2 Visibility
- Types in `domain`, `application`, `persistence` and `web` are package-private unless a *neighbouring package of the same module* must use them (then `public`). Controllers, listeners and `Impl` classes are package-private.
- Types in `api` are public.

### 2.3 Aggregate rules (from DDD slide 67)
1. Only the **aggregate root** has a repository. Entities inside an aggregate (`Membership`, `Invitation`, `LedgerEntry`) have none.
2. Every state change and every business rule is a method on the root. A service or controller never constructs or mutates a child.
3. A root method is named after the **operation in the diagram** (`acceptInvitation`, `record`, `closeForSettlement`) and **returns the domain event it produces** (or a small result record when it produces several). An operation that reacts to another context's event and produces no event of its own returns nothing (`Group.updateBalanceStatus` is `void`; the diagram draws no event for it). It never publishes; it never calls another module.
4. Other aggregates and other modules are referenced **by id** (`GroupId`, `UserId`) only.
5. The application service is thin and always has this shape: load the root (lock it where a race matters) → call one root method → save → publish the returned event(s) → return a result.
6. Time comes in as a parameter (`Instant now` from the injected `Clock`), never from `Instant.now()` inside the domain.

### 2.4 Value objects and where they live
Value objects wrap every id and every concept the diagram draws as a value object. No raw `UUID`, `String` or `long` in a domain signature where a value object exists.

| Lives in | Types |
|---|---|
| `shared` (used by 2+ modules) | `UserId`, `GroupId`, `Money`, `Email` (`Currency` is `java.util.Currency`) |
| `<module>.api` (appears in an event or a port) | `InvitationId`, `ExpenseId`, `ExpenseShare`, `SettlementPaymentId` |
| `<module>.domain` (stays inside) | `GroupName`, `ExpenseTitle`, `EntryId`, `DirectedDebt`, `DisplayName` |

**Rule:** a value object that appears in an event or in an `api` port lives in `api`, because `api` may not import its own module's `domain`. Everything else stays in `domain`.

- Ids are records with one field `value` of type `UUID`: `record GroupId(UUID value)`.
- **There is exactly one identity type for a person: `UserId`.** There is no `MemberId`. A person's role in a group is the `Membership` entity, which holds a `UserId`.
- `Money` is `record Money(BigDecimal amount, Currency currency)`. The amount always has the currency's number of fraction digits (EUR: 2) and is rounded `HALF_EVEN` only where a rule says so (equal split: the remainder goes to the first participants, in order, so shares always add up exactly). The type offers `plus`, `minus`, `isPositive`, `isZero`, `negate`. Never `double`/`float`, never `new BigDecimal(double)`.
- Value objects validate in their constructor and throw `DomainException` with a stable `ErrorCode`.
- **Every event ends with `Instant occurredAt`** and carries ids and value objects only, never entities or names.
- An `api` read model exposes shared value objects and plain text; a module's own domain value objects never leave it (`UserProfile.displayName` is a `String`, the account's `DisplayName` stays inside).

## 3. The vocabulary: names come from the diagram

Use these names verbatim, in code, tests, JSON, SQL and docs. English, `UpperCamelCase` for types, `lowerCamelCase` for fields and methods, `UPPER_SNAKE_CASE` for enumeration constants, `snake_case` for SQL.

### 3.1 Group context
- Aggregate root `Group`: fields `id: GroupId`, `name: GroupName`, `currency: Currency`, `status: GroupStatus`; entities `Membership` (`userId`, `role: GroupRole`, `status: MembershipStatus`, `hasZeroBalance`, `joinedAt`; plus `balanceStatusAt`, not in the diagram, see 5.1) and `Invitation` (`id: InvitationId`, `invitee: UserId`, `role: GroupRole`, `status: InvitationStatus`, `expiresAt`). An invitation belongs to one existing account and only that account can answer it; `EXPIRED` is derived from `expiresAt`, never stored. Only an **admin** invites (`NOT_AN_ADMIN` otherwise) and chooses the `role` the invitee gets on accepting (`MEMBER` unless stated). `promoteToAdmin` is admin-only and one way: there is no demotion (design 2026-11-09).
- Operations (each returns its event, except `updateBalanceStatus`, which reacts to `MemberBalanceStatusChanged` and returns nothing, see 2.3): `createGroup`, `inviteMember`, `acceptInvitation`, `declineInvitation`, `promoteToAdmin`, `removeMember`, `leaveGroup`, `closeForSettlement`, `reopenGroup`, `archiveGroup`, `updateBalanceStatus`.
- Enumerations `GroupStatus {OPEN, CLOSED_FOR_SETTLEMENT, ARCHIVED}`, `GroupRole {ADMIN, MEMBER}`, `MembershipStatus {ACTIVE, FORMER_MEMBER}`, `InvitationStatus {PENDING, ACCEPTED, DECLINED, EXPIRED}`.
- Events (`MemberInvited` carries `role`, `MemberPromoted` is `groupId, userId, promotedBy, occurredAt`): `GroupCreated`, `MemberInvited`, `MemberJoined`, `MemberLeft`, `MemberRemoved`, `MemberPromoted`, `InvitationDeclined`, `GroupClosedForSettlement`, `GroupReopened`, `GroupArchived`.
- Read model `GroupMembershipView`.

### 3.2 Expense context
- Aggregate root `Expense`: `id: ExpenseId`, `groupId`, `paidBy: UserId`, `recordedBy: UserId`, `title: ExpenseTitle`, `totalAmount: Money`, `splitStrategy: SplitStrategy`, `status: ExpenseStatus`, `shares: List<ExpenseShare>`.
- `ExpenseShare(participantId: UserId, owedShare: Money)`.
- Operations: `record`, `correct`, `remove`. Enumerations `SplitStrategy {EQUAL, EXACT, PERCENTAGE}`, `ExpenseStatus {ACTIVE, CORRECTED, REMOVED}`.
- Events: `ExpenseRecorded`, `ExpenseCorrected`, `ExpenseRemoved`.
- Rule R18: the person who records an expense (`recordedBy`) is always one of its participants (`RECORDER_NOT_PARTICIPANT`, 422). It overrides R3's "the payer may or may not be a participant" for the recorder only: Alice cannot record "I paid for Bob and Carol" for herself; Bob records it with Alice as `paidBy`.

### 3.3 Settlement context
- Aggregate root `GroupLedger` (`groupId`, entries) with append-only entity `LedgerEntry` (`id: EntryId`, `userId`, `deltaAmount: Money`, `type: LedgerEntryType`, `referenceId`, `occurredAt`); operations `appendExpenseEntry`, `appendCorrectionReversal`, `appendRemovalReversal`, `appendSettlementPayment`, `appendDebtWaiver`, `projectBalances`, `projectSimplifiedPlan`.
- Aggregate root `SettlementPayment` (`record`, `confirm`, `reject`, `waive`; `record` and `waive` are factories that create the payment, `waive` already as WAIVED, see `docs/diagram-deltas.md` S1), enumeration `PaymentStatus`.
- Read models `MemberBalance(userId, netBalance)`, `SettlementPlan(simplifiedDebts)`, value object `DirectedDebt(fromDebtor, toCreditor, amount)`.
- Events: `SettlementPaymentRecorded`, `SettlementPaymentConfirmed`, `SettlementPaymentRejected`, `DebtWaived` (in `settlement.api`); `MemberBalanceStatusChanged`, `AllBalancesSettled` (published by settlement, but in `shared.events`, see 5.1).

### 3.4 Account context
- Aggregate root `Account`: `id: UserId` (the Keycloak subject), `email`, `username: Username` (lower case, 3 to 30 characters of `a-z 0-9 . _ -`, never an `@`; chosen at sign-up at Keycloak, copied on first login), `displayName: DisplayName`, `status: AccountStatus`, `registeredAt`; operation `registerFromIdentityProvider`, … (the rest of the diagram arrives in later exercises). Passwords, sign-in, lock-out and tokens belong to the identity provider (Keycloak); ShareWise has none of them.
- Read model `UserProfile(userId, displayName, username, isActive)`, served by a port that offers `findProfiles(Set<UserId>)` and `findByIdentifier(String)` (exact username or email, trimmed, case-insensitive, never partial).
- Events: none yet; the diagram draws none for the Account context.

### 3.5 Words we do not use
`User` for the aggregate (it is `Account`; `UserId` and `UserProfile` are fine), `Member` for a person (it is the `UserId` of an active `Membership`), `Share` alone (it is `ExpenseShare`), `Role` alone (`GroupRole`), `payer`/`payerId` (it is `paidBy`), `amountCents`/`Cents` (money is `Money`), `Dto`, `Manager`, `Helper`, `Util`, `Data`, `Info`, `Impl` for anything except an `api` port implementation.

### 3.6 Notification context
- Aggregate root `Notification`: `id: NotificationId`, `recipient: UserId`, `type: NotificationType`, `groupId: GroupId`, `actor: UserId` (who did it), `reference: UUID` (what it points at), `occurredAt`, `readAt` (null = unread); operations `notify` (factory) and `markRead`. It stores ids only; names and texts are looked up when the inbox is read.
- Enumeration `NotificationType {MEMBER_JOINED, INVITATION_DECLINED, MEMBER_PROMOTED, PAYMENT_CONFIRMED, PAYMENT_REJECTED, DEBT_WAIVED, EXPENSE_RECORDED}` (`MEMBER_PROMOTED`: recipient the promoted person, actor the promoting admin, reference the promoted person's id); later `MEMBER_REMOVED`. A received invitation is not an entry: it is state, asked from the group context (`GET /invitations`). A payment waiting for the recipient's answer is not an entry either: it is state, asked from the settlement context (`GET /me/settlement-payments`).
- Consumes `MemberJoined` and `InvitationDeclined` (to the inviter), `MemberPromoted` (to the promoted person) and `SettlementPaymentConfirmed`, `SettlementPaymentRejected`, `DebtWaived` (to the debtor, `fromDebtor`; actor the creditor; reference the payment id) and `ExpenseRecorded` (to everybody involved except the recorder, that is the participants and the payer; actor the recorder; reference the expense id; the event carries `recordedBy` for this). `NotificationListener` has one asynchronous `on(Event)` method per event and delegates to `NotificationService`. Uses the ports `UserProfiles` and `GroupDirectory`.

## 4. Class naming by role (checked by `ConventionTests`)

| Role | Name | Package | Example |
|---|---|---|---|
| Aggregate root, entity | the diagram name | `domain` | `Group`, `Membership` |
| Value object, enumeration | the diagram name | `domain` or `shared` | `GroupName`, `GroupRole` |
| Domain event | past tense, diagram name, a `record` | `api` of the publisher (only exception: `shared.events`, 5.1) | `ExpenseRecorded` |
| Use-case service | `<Aggregate>Service`, one per aggregate | `application` | `ExpenseService` |
| Command | `<Operation>Command`, a `record` | `application` | `RecordExpenseCommand` |
| Listener for events | `<Aggregate>Listener`, one per module and aggregate, one `on(<Event>)` method per event | `application` | `GroupLedgerListener`, `NotificationListener` |
| Port other modules call | a noun or noun phrase, an `interface` | `api` | `GroupMembership`, `Balances`, `UserProfiles`, `CurrentUser` |
| Port implementation | `<Port>Impl` | `application` | `BalancesImpl` |
| Read model | the diagram name, a `record` | `api` (if returned across modules) else `domain` | `MemberBalance` |
| Repository | `<Aggregate>Repository` | `persistence` | `GroupRepository` |
| Controller | `<Aggregate>Controller` | `web` | `GroupController` |
| HTTP request / response | `<Operation>Request`, `<Thing>Response`, `record`s | `web` | `RecordExpenseRequest`, `ExpenseResponse` |
| Configuration | `<Module>Config` (or `<What>Config` for a cross-cutting concern such as security) | module root, package-private | `GroupConfig`, `SecurityConfig` |
| Servlet filter, Spring Security converter | `<What>Filter`, `<What>Converter` | `application` | `JwtCallerConverter` |

Method names on services are the operation names (`recordExpense`, `acceptInvitation`); queries are `find…`/`list…`/`get…` (`get` throws when absent, `find` returns `Optional`, `list` returns a list). Boolean methods read as a question (`isMember`, `hasZeroBalance`).

## 5. Communication between modules

- **Synchronous question** ("is this user in the group?"): call an `api` port. Ports are read-only, answer with plain values (never entities), and work without an open transaction.
- **Event** ("an expense was recorded"): the publishing module's `application` service publishes the event with `ApplicationEventPublisher` inside its transaction; the consuming module reacts in a `<Aggregate>Listener` with one `@ApplicationModuleListener` method per event (asynchronous, after commit, each method with its own entry in the event publication registry). Consumers are idempotent (a repeated event changes nothing).
- Events carry **ids and value objects, never names or entities**. Display names are looked up at read time through the `UserProfiles` port.
- Balances are therefore eventually consistent with expenses; the frontend and tests are written for that (document 02 and 04).
- How events are delivered today, why a scheduled redelivery was skipped on purpose, and what to keep in mind for the move to a message broker: `docs/notes/2026-10-27-event-delivery-and-messaging.md`.

### 5.1 The one exception: `shared.events` (D12)

Events live in the `api` of the module that publishes them, **except** `MemberBalanceStatusChanged` and `AllBalancesSettled`, which live in `de.hsh.favs.sharewise.shared.events`. Settlement publishes them after every ledger booking (expense, confirmed payment, waiver) when a zero-state flips; group consumes `MemberBalanceStatusChanged` to keep `Membership.hasZeroBalance` (R11) up to date. `AllBalancesSettled` has no consumer yet (auto-archive L5 is parked).

- **Why:** the module graph stays one-way, `account ← group ← expense ← settlement`. Settlement already depends on `group::api`; a group listener for an event in `settlement.api` would make group depend on settlement, a cycle `ModularityTests` refuses. A survey of all diagram events found these two as the only ones that flow upstream.
- **Alternatives rejected:** allow `group → settlement` (a cycle, and it blocks the P3 service split); a `canLeave` port in settlement (group would call settlement, the same cycle); settlement keeping its own copy of group data (the biggest change for the least gain).
- **Order:** the events are asynchronous and may arrive out of order, so a `Membership` stores when its flag was last set (`balance_status_at`) and ignores an older event.
- **Checked by** `ConventionTests.sharedEventsAreExactlyTheTwoBalanceEvents`: every class in `shared.events` is a record, and the names are exactly these two. Adding a third is a design decision, not a convenience.
- **Change driver (P2):** if more events need to flow upstream, the split between group and settlement is probably wrong; revisit the boundaries instead of growing this package.

## 6. Persistence and database

- One Flyway folder per module: `db/migration/<module>/V<n>__<what>.sql`. Version ranges: shared `V0xx`, account `V1xx`, group `V2xx`, expense `V3xx`, settlement `V4xx`, notification `V5xx`. **Never edit a migration that is on `main`; add a new one.** Check the open branches for a version you are about to use (two branches with the same version make Flyway refuse to start after the merge).
- Because every module numbers its own migrations, Flyway runs with `out-of-order: true` (`application.yml`). Without it a new `V104` is refused by any database that already has `V401`. Found when the first refactoring ran on a real database; fresh test databases hide it.
- Tables `snake_case`, plural (`accounts`, `groups`, `memberships`, `expenses`, `expense_shares`, `settlement_ledgers`); columns `snake_case`; ids `UUID`; money `NUMERIC(19,2)` named `<name>` (not `_cents`); instants `TIMESTAMPTZ` named `<what>_at`; enumerations stored as `VARCHAR` of the constant name.
- Every migration starts with a `--` comment saying why it exists; every non-obvious column, index and constraint gets a one-line `--` why.
- `spring.jpa.hibernate.ddl-auto=validate`: the schema comes from migrations only.
- No foreign keys between modules. A column that references another module holds the id as a plain `UUID`.
- Lazy collections are never read outside a transaction; ports that other modules call use queries (see document 02, pitfalls).

## 7. HTTP API

Defined first in `docs/api/openapi.yaml`; the Java records in `web` and the TypeScript types are generated from or checked against it.
- JSON field names equal the diagram names (`paidBy`, `totalAmount`, `splitStrategy`, `userId`, `participantId`, `owedShare`, `netBalance`).
- Money in JSON is a **decimal string with exactly two fraction digits**, e.g. `"900.00"` (JSON numbers would turn into binary floats in JavaScript). (call)
- Ids are UUID strings; instants are ISO-8601 UTC; dates are `YYYY-MM-DD`.
- Errors are `application/problem+json` with a stable `code` from the `ErrorCode` enumeration; the contract lists every code an endpoint can return.
- Status codes: `201` create, `200` read/update, `204` no content, `400` malformed or invalid input, `401` not signed in, `403` signed in but not allowed, `404` unknown, `409` conflict, `410` expired, `422` a business rule refused it.
