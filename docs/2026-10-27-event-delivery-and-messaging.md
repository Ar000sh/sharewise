# Decision: event delivery today, and what to keep in mind for messaging later

**Date:** 27.10.26 · **Status:** decided for Ex4/Ex5 · **Origin:** findings B2/B3/D5 of the independent review, discussed with the team

## 1. How an expense reaches the balances today
1. `ExpenseService.recordExpense` saves the expense and publishes `ExpenseRecorded` **in the same database transaction**. Spring Modulith writes the event to the table `event_publication` as part of that transaction. A saved expense therefore always has an event waiting. This is a small **transactional outbox**.
2. After the commit, `ExpenseRecordedListener` (`@ApplicationModuleListener`, asynchronous, own transaction) books the amounts in the ledger of the group.
3. When the listener finishes, Modulith marks the event as completed.

Balances are therefore **eventually consistent** with expenses: for a short moment after recording, the balances can still be the old ones.

## 2. What can go wrong
If the listener fails (database connection lost, deadlock, a bug), the event stays *incomplete* in `event_publication`. The expense exists and shows in the expense list; the ledger never gets the booking; the balances stay wrong and nobody sees an error.

## 3. Decision
| What | Decision |
|---|---|
| Redeliver incomplete events when the application starts | **Yes.** `spring.modulith.events.republish-outstanding-events-on-restart: true` in `application.yml`, pinned by a test in `SharewiseApplicationTests`. |
| Redeliver incomplete events on a schedule while the application runs | **No, deliberately skipped.** |
| Make the user aware that balances may lag | **Yes.** A toast after recording ("Expense saved. Balances update in a moment.") and a bounded re-fetch of the balances (0.5, 1, 2, 4 seconds, with "Updating…"). |

## 4. Why the scheduled redelivery is skipped
- **It would be thrown away.** The plan (Ex9–Ex11) is to move to messaging with a broker such as RabbitMQ, the outbox pattern and a saga. The table `event_publication` already is the outbox. A job that retries unfinished events in-process does the work the broker takes over then: delivery is retried by the broker and the consumers, with dead-letter queues for messages that keep failing. We would build it in November and delete it in December.
- **It would only help with temporary failures.** A connection drop or a deadlock is cured by trying again. A bug in the booking code fails every time, and a retry every minute just fails every minute (the review's B2 scenario).
- **Failures are rare** in a single-instance application on a laptop.
- **The limitation is good presentation material.** For P2 ("limits and change drivers") it is evidence of why a monolith with in-process events is not enough: *"events that fail are only delivered again on restart; the outbox and the message broker close that gap in P3."*

## 5. Consequences we accept (write them into P2)
- After a failed booking the balances stay wrong until the next restart.
- The user cannot see that a booking failed. The re-fetch hides the normal short lag only.
- Completed rows in `event_publication` are never cleaned up, so the table only grows.

## 6. What to keep in mind when we move to messaging (Ex9–Ex11)
These are things to **do or avoid already now**, so that the move is a small step.

1. **Every handler must tolerate the same message twice.** Brokers deliver at least once. Our ledger already skips an expense it has booked (`isBooked`), but it keys on the *expense id*. A correction or removal of the same expense (Ex5) would look "already booked" and be dropped silently, and a second reversal would violate the unique constraint and crash the listener. **Give every event its own identity** (an event id, or a revision number per expense) and make the guard and the unique constraint use that. This is the open decision D2/D5 of the review; decide it before Ex5 starts.
2. **Events are a published contract.** They become messages that other services read. Keep them plain data: ids, value objects, `occurredAt`, never entities or names (already a convention). Do not rename or move event classes casually: the stored rows contain the Java class name, so a rename breaks the redelivery of old rows. When messages leave the process, give them an explicit type name and a version.
3. **Do not rely on order across events.** A removal must not be applied before the expense it removes. Revision numbers on the events (point 1) let a handler detect and park an out-of-order message. Queues keep order per queue, not across queues or after a retry.
4. **Synchronous calls between modules will not survive the split.** `GroupMembership` is called on every expense. As separate services this becomes an HTTP call per write (availability coupling). The design already prepared the way: `Expense.record` takes a `GroupMembershipView`, which can later come from a local copy fed by group events, without changing the domain.
5. **Plan for messages that never succeed.** A message that fails again and again needs a limited number of retries with back-off and then a dead-letter queue, plus a way to look at it. In the monolith nobody sees a failed event; in P3 that must be visible (a metric or log line for "incomplete publications").
6. **Publishing and saving must stay in one transaction.** That is what the outbox gives. When a service publishes straight to RabbitMQ from its code, an expense can be saved without the message or the message sent without the expense. Keep writing to the outbox table first and let a relay send from there. (note: Spring Modulith can externalize events to AMQP with `@Externalized` and `spring-modulith-events-amqp`; check the current documentation when Ex9 starts, this is not verified yet.)
7. **Consumers need their own tests for duplicates and disorder.** The tests that exist today for the listener (a delivered-twice event books once) are the template; add "arrives in the wrong order" and "fails, then succeeds" when messaging arrives.

## 7. Open items
- Decide the event identity and revision model (D2/D5 of the review) before Ex5.
- Add the three consequences of section 5 to the P2 preparation.
- When Ex9 starts: check Spring Modulith's AMQP externalization against the lecture's outbox material.
