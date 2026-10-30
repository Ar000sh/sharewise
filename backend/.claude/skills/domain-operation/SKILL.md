---
name: domain-operation
description: Add or change one operation on a ShareWise aggregate the way the conventions require - diagram name, root method that returns its event, thin service (load, call, save, publish), listeners in other modules, Javadoc with rule numbers, tests at the right levels, diagram delta. Use for any new business operation such as correctExpense, leaveGroup, closeForSettlement, recordSettlementPayment.
---

# Domain operation (backend)

Why this shape: the aggregate protects its invariants and only says what happened (it returns the event); the service handles transactions and publishing; other modules react to the event. That keeps the module ready to become a service later (the event becomes a message).

## Steps
1. **Name and rule.** Find the operation in the diagram (`work/ShareWise_Domain_Model.drawio`, FAVS folder) and `docs/conventions/01` section 3, and the rules it enforces (R-numbers in `work/ShareWise_Requirements.md`). If the diagram lacks it, stop and ask: add it to the diagram, or rename. List the event it produces and who listens.
2. **Domain unit test first** (`<module>/domain/<Aggregate>Test`): the allowed case with the returned event, each rejected case with its `ErrorCode` and the state unchanged. Watch it fail.
3. **Root method** in `domain`: named after the operation, takes value objects and `Instant now`, checks the rules, changes state, returns the event (a record in `<module>/api`, ending with `Instant occurredAt`, ids and value objects only). Children only through the root. Javadoc: the rule, `(R..)`, the returned event, `@throws` codes.
4. **New `ErrorCode`?** Use `/contract-change` step 6 (enum, contract, frontend message).
5. **Service test** (`application`, Mockito): loaded, saved, published in that order; a rejected call saves and publishes nothing. Then the **service method** in exactly the shape load (lock if two requests can race) → call the root → save → publish → return.
6. **Migration** if the state has a new column or table (`.claude/rules/migrations.md`).
7. **Listeners** in other modules for the event: `<Event>Listener` with `@ApplicationModuleListener`, idempotent (a repeated event changes nothing), with a test that delivers the event twice. A new module dependency needs the user's yes (`package-info.java`).
8. **Endpoint** if the operation is exposed: `/contract-change`.
9. **Integration:** a controller test for status and JSON; extend `MallorcaSliceTest` if the operation is part of the journeys.
10. **Diagram delta:** if a name or field changed, note it for the diagram update (`docs/diagram-deltas.md`) in the same change.
11. `./mvnw -q verify`, then commit (`feat(<module>): <operation>`, tests in the same commit).
