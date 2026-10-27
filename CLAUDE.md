# ShareWise (code repository)

Spring Boot modulith (`backend/`) + Next.js (`frontend/`) for the FAVS module. Study notes live in the separate FAVS repo; this repo holds only code and its docs.

**Before writing code, read `docs/conventions/README.md` and the document for the kind of change you make.** The rules are binding and partly checked by the build. In short:
- Names come from the domain diagram (`work/ShareWise_Domain_Model.drawio` in the study repo): `Account`, `Group`/`Membership`, `Expense`/`ExpenseShare`, `GroupLedger`/`LedgerEntry`; `UserId` is the only person identity; money is `Money(BigDecimal, Currency)`.
- Every module has `api/ domain/ application/ persistence/ web/`. Aggregates return their events, services publish them. Modules talk only through `api`.
- Contract first: `docs/api/openapi.yaml` changes in its own commit, then `npm run gen:api`.
- Tests: sentence-named methods, `// given // when // then`, fixtures, real sign-up instead of any shortcut; see `docs/conventions/02_TESTING.md`.
- Commits: `<type>(<scope>): <subject>`; one concern each; `main` stays green.

Commands: see `RUNNING.md` (start, test, e2e). Docker Desktop must run for backend tests.
