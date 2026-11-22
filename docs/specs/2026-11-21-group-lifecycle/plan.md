# Implementation and verification plan

1. Define and lint the API contract; regenerate frontend types.
2. Implement group transitions, immutable archive/history children, and migrations.
3. Serialize financial writes with closure and verify ledger booking completion.
4. Implement title/amount correction preserving original expenses and payments.
5. Connect lifecycle notifications, proxy routes, archive views and forms.
6. Verify domain rules, HTTP authorization, concurrent closure, delayed booking, snapshot immutability, frontend interactions and existing regression tests.
7. Keep changes on the feature branch for review; do not deploy or push automatically.
