---
paths:
  - "backend/src/main/resources/db/**"
---

# Flyway migrations

- **Never edit a migration that is on `main`** (the guard hook blocks it). Add a new file with the next free version of the module's range: shared `V0xx`, account `V1xx`, group `V2xx`, expense `V3xx`, settlement `V4xx`, in `db/migration/<module>/`.
- `out-of-order: true` is on, so module ranges may interleave; never renumber.
- Tables plural `snake_case`; ids `UUID`; money `NUMERIC(19,2)`; instants `TIMESTAMPTZ` named `<what>_at`; enumerations as `VARCHAR` of the constant name.
- No foreign keys across modules: a column that references another module holds a plain `UUID`.
- Every non-obvious column, index and constraint gets a one-line SQL comment saying why (`-- one ledger per group (GroupLedger is the aggregate root)`).
- `ddl-auto=validate`: an entity change without its migration fails at start-up. Run the module's database tests after adding one.
- A schema change is shared state: say what it changes before writing it (root CLAUDE.md, "Who decides what").
