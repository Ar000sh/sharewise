---
paths:
  - "backend/src/main/java/**/domain/**"
---

# Editing a domain package

Checklist from `docs/conventions/01` section 2.3 and `03` section 2. ArchUnit catches the imports; the rest is on you.
- The name is in the diagram (`work/ShareWise_Domain_Model.drawio`, FAVS folder). If it is not, stop and ask: either the diagram gets the concept or the code is wrong.
- Every state change is a method on the aggregate root, named after the diagram operation, and **returns** its event (it never publishes, never calls another module). Children (`Membership`, `Invitation`, `LedgerEntry`) are never constructed or changed from outside the root.
- Time is a parameter (`Instant now`), never `Instant.now()`. No Spring, no servlet, no repository imports.
- Other aggregates by id only (`GroupId`, `UserId`). Money only as `Money`, never `double` or `new BigDecimal(double)`.
- A rule that fails throws `DomainException` with an `ErrorCode`; a new code also goes into the contract, `ErrorCode` and `frontend/src/lib/errors.ts` (use `/contract-change`).
- Javadoc on each operation: the rule in one sentence, the rule numbers `(R4)`, the event it returns, the `@throws` codes.
- Every rule gets a domain unit test, including the rejected case and what did **not** happen.
