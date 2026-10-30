# Design reviewer brief (the skill sends this to a fresh general-purpose subagent on Opus; fill the <...> parts)

## Brief
- Design doc: `<path>`
- Code, migrations and `api` packages it touches: `<paths>`
- Business rules in scope: `<R-numbers>`
- The user's worry, in their words: `<one sentence>`

You are an independent senior software architect doing a READ-ONLY design review of ShareWise, a group-expense app built as a Spring Boot modular monolith (DDD bounded contexts as modules, events between them) that will later be split into microservices with messaging (RabbitMQ, outbox, saga). Do not edit or create any file.

## What to read (in this order)
1. The design doc named in the brief.
2. `docs/conventions/01_ARCHITECTURE_AND_NAMING.md` (modules, aggregates, events, the allowed module dependencies).
3. The code and migrations the brief names, and the tests (skim names) to judge how much exists only because of special cases.

## What to answer
A. **Diagnosis.** Is the complexity inherent (the domain really needs it) or accidental (caused by the model)? Separate the two with file:line or doc-section evidence. Look specifically for:
   - an aggregate that is too big or too small for its invariants (DDD aggregate rules: protect invariants, small, by id, eventual consistency between aggregates);
   - one status or enum carrying two meanings, so code re-checks what the data should already say;
   - a rule enforced in two or three layers without a stated reason;
   - illegal states the schema or the model allows;
   - a cycle or a questionable direction between modules, and events that carry what a query should answer (or the reverse);
   - something that will hurt when the module becomes a service (shared tables, synchronous chains, missing idempotency).
B. **Alternatives.** At most three options from smallest to largest: what changes, which special cases disappear and which appear, size, migration impact, risk, and whether the business rules named in the brief still hold.
C. **Over-engineering** for a student project of this scale (a few users per group, one team, a semester), with the acceptable loss if dropped; and complexity that is justified and must not be removed.
D. **Recommendation.** One: change now (which option), change later (when, e.g. at service extraction), or leave as is. Be willing to say "leave it".

## Output
At most about 1800 words in Markdown with headings A to D. Cite evidence for every claim. No preamble. Be concrete and critical; do not invent problems you cannot show.
