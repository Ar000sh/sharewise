# Group lifecycle: business reasoning and proposed detailed rules

Created: 20 November 2026  
Updated: 21 November 2026

Status: **Implemented on `feature/group-lifecycle-archives`.** This document preserves the business reasoning and distinguishes confirmed decisions from supporting proposals. The implemented scope, selected defaults, consistency guarantees and limitations are described in [the implementation design](../specs/2026-11-21-group-lifecycle/design.md). Requirements and domain terminology have been updated with the implementation.

## 1. The agreed model

> Only an admin can close a group, and only after every balance is zero. Closing automatically creates an archive version. Only an admin can reopen the group for corrections. Closing it again creates a new archive version while preserving the previous one.

There are **two group states: Open and Closed**. An archive is a preserved record of a successful closure, rather than a third group state.

The later decision removes the previously considered “Settling up” lifecycle phase. Payment recording and confirmation still exist, but happen while the group is Open.

| Topic | Confirmed decision |
|---|---|
| Close permission | Active group admins only |
| Balance prerequisite | Every relevant person's balance must be zero |
| Archive creation | Automatic consequence of successful closure |
| Reopen permission | Active group admins only, for corrections |
| Reopen explanation | Require a reason, as agreed earlier |
| Repeated closure | Creates a new archive version |
| Previous archives | Preserved; later corrections do not overwrite them |
| Closing warning | Explain that changes will be blocked and reopening requires an admin |
| Separate settlement phase | Removed |

The user's preference for admin-controlled membership is retained. Exact membership permissions after closure still need a separate decision; they should not be inferred from lifecycle permissions.

## 2. Is archival still automatic?

**Yes. Archive creation is automatic; the decision to close is manual.**

| Trigger | Result |
|---|---|
| Last balance reaches zero | Group stays Open; closure may now be available |
| Admin successfully closes | Group becomes Closed; an archive version is created automatically |
| Admin reopens for corrections | Group becomes Open; archives remain unchanged |
| Admin closes again after corrections and repayment | Group becomes Closed; a new archive version is created automatically |

There is no separate “Create archive” action. There is also no automatic closure just because balances reach zero.

Original rule L5 ties automatic archival to the last balance reaching zero. The revised model retains automation but changes its trigger to **successful admin closure**. This is a requirements change, not merely a different description of the original rule.

## 3. Why close only at zero?

Closing now means:

> The recorded financial obligations are settled, and an admin has decided that work on this group is complete for now.

It no longer means “freeze expenses so that repayment can start.”

Example:

1. Alice pays EUR 120 for accommodation shared equally with Bob.
2. Alice has a EUR 60 credit; Bob owes EUR 60.
3. Bob records his repayment while the group is Open.
4. Alice confirms receipt, and the ledger processes the payment.
5. Both balances reach zero. The group remains Open.
6. An admin reviews the summary and confirms closure.
7. The group becomes Closed, and archive v1 is created automatically.

An ongoing household group can reach zero repeatedly without closing. A trip group can be closed when its admin decides it is finished.

### Tradeoff: expenses remain editable during repayment

Removing the settlement phase removes its expense freeze. An authorized member may change expenses while another member is arranging payment.

Confirmed payments must remain recorded, pending payments must be considered when validating further payments, and outstanding balances must reflect corrections. The application must not erase a payment to make it fit a changed expense.

Whether corrections should be temporarily blocked while payments are pending remains a supporting policy decision. The lifecycle change does not solve that interaction by itself.

## 4. Group state and archive versions are different things

### Open

The working group accepts authorized expense and payment operations. Its balances can change. After reopening, current contents may differ from the latest archive.

### Closed

Financial changes are blocked until an admin reopens the group. Authorized users can view history and archives. Admin reopening is the explicit exception to the closed-state restriction.

### Archive version

An archive preserves the financial facts at one successful closure. Its financial contents are immutable.

Reopening changes the working group; it does **not** unlock an old archive for editing. Corrections appear in the next archive when the group closes again.

```text
Open -- admin closes at zero --> Closed + archive v1
                                   |
                            admin reopens with reason
                                   |
                                   v
Open -- corrections and repayments --> Open at zero
                                   |
                             admin closes again
                                   |
                                   v
                            Closed + archive v2

Archive v1 remains available throughout.
```

The interface should distinguish current working data, the latest archive, and previous archives. While reopened, explain that current data may differ from the latest archive. Once v2 exists, v1 can be presented as superseded without altering its financial content.

## 5. Correcting an error after closure

Zero balances prove that recorded obligations are settled. They do not prove that the recorded expenses are correct.

Consider a receipt entered as EUR 100 instead of EUR 80:

1. Alice's EUR 100 expense is split equally with Bob.
2. Bob pays Alice EUR 50; Alice confirms receipt.
3. After ledger processing, balances are zero. An admin closes, creating archive v1.
4. The incorrect receipt amount is discovered.
5. An admin reopens with the reason “Receipt entered as EUR 100; actual amount EUR 80.”
6. An authorized person corrects the expense through the expense correction workflow.
7. Bob's confirmed EUR 50 payment remains recorded. His correct share is EUR 40, so Alice now owes Bob EUR 10.
8. Alice records a EUR 10 repayment; Bob confirms it.
9. Once balances are zero and processing is complete, an admin closes again.
10. Archive v2 records the corrected facts and payment history. Archive v1 remains the earlier snapshot.

Reopening does not itself correct expenses or reverse payments. It permits authorized operations to do so.

Other reasons include duplicate expenses or incorrect participants. A merchant refund may require a dedicated accounting operation; reopening does not automatically add refund support to the existing positive-expense model.

## 6. Detailed closure rules

### Actor and source state

The caller must be an active admin and the group must be Open. The backend enforces these conditions regardless of which UI buttons are visible.

### Financial prerequisites

The confirmed zero-balance rule is supplemented by these proposed correctness safeguards:

1. Every relevant person's authoritative net balance is exactly zero.
2. No payment awaits confirmation or rejection.
3. Every accepted operation affecting the snapshot has been processed.
4. Closure and archive creation use the same consistent financial revision.

These prevent a temporarily zero group from closing while an accepted operation can still create debt. Admin authority does not override them.

Include relevant historical ledger participants; filtering only current members must not hide former members' debts.

**Checking the sum is insufficient:** Alice +EUR 60 and Bob -EUR 60 sum to zero, but each still has an unsettled position. Use the application's money representation and currency precision, not floating-point tolerances.

### Summary and warning

Show expenses, payment history, balances, and unresolved processing or payments. Explain blockers instead of presenting provisional balances as final.

Suggested warning:

> Closing creates an archive of the group's current finances and prevents further changes. Only an admin can reopen the group for corrections. Closing it again creates a new archive version; this version will remain available.

Cancelling changes nothing. Confirming must revalidate conditions on the server because the displayed summary can become outdated.

### Successful result

- Group becomes Closed.
- Exactly one archive version is durably associated with the successful closure.
- Closing admin, time, version, and source revision are recorded.
- Members are notified and can access the archive subject to access rules.

There must be no user-visible successful closure with a missing, unrecoverable archive snapshot. The transaction or recovery mechanism belongs to technical design. Generating an optional downloadable export can be separate from persisting the underlying snapshot.

### Failure and retries

Unauthorized calls, debt, pending payments, and incomplete processing must not produce a successful closure or completed archive version.

Duplicate requests for the same closure must not create more versions. A delayed retry from an earlier closure must not silently close a newly reopened group either; requests need protection against acting on a later revision. Exact API retry behavior is still to be designed.

## 7. Detailed reopening rules

### Preconditions

- Caller is an active admin.
- Group is Closed.
- A nonblank reason is supplied.

The earlier agreement to resolve pending payments before reopening is normally already satisfied: a correctly closed group has no pending payments. Inconsistent legacy records need explicit recovery handling rather than bypassing financial safeguards.

### Effects

- The working group becomes Open.
- Record the admin, reason, time, and preceding archive version.
- Preserve all archives and confirmed payments.
- Notify members that corrections may change amounts owed.
- Restore ordinary operation-specific permissions.

Reopening does not grant everyone permission to edit every expense. Existing recorder, payer, and admin rules still apply.

Reopening alone creates no archive. The next successful closure does. Under this model, reopening and closing without financial changes still creates a new closure/version; lifecycle history explains why it happened.

Ordinary participants cannot close or reopen. They can ask an admin to address an error. An in-app correction-request workflow has not been agreed as part of this feature.

## 8. What the archive preserves

Preserved versions are agreed. The following minimum contents are a proposed specification:

| Content | Purpose |
|---|---|
| Group ID and version | Identify the closure |
| Closing admin and timestamp | Explain who finalized it and when |
| Group name and currency at closure | Preserve context |
| Relevant participants and historical identities | Preserve financial attribution |
| Expense values, participants, correction/removal history | Explain the financial basis |
| Payment and waiver history with relevant statuses | Explain how balances were settled |
| Per-person balances at closure | Demonstrate the zero condition |
| Source revision and previous archive reference | Connect closures and support consistency |

Pointers to mutable current expenses are insufficient. Old archives must reproduce their original values after corrections. Implementation could use copied snapshot data or immutable historical revisions.

An archive need not initially be a PDF. A durable, viewable version in the application satisfies the concept. Export format, storage, retention, and former-member access remain to be specified.

## 9. Permissions and agreement

| Decision | Authority |
|---|---|
| Close or reopen | Active admin |
| Confirm payment receipt | Creditor under existing payment rules |
| Correct an expense | Existing expense authorization rules |

Unanimous member approval is not a prerequisite for closure in the agreed model. Notifications provide visibility, but do not imply consent or payment receipt.

Admin membership powers do not permit erasing debts or historical participation. Existing balance and last-admin safeguards remain relevant.

### Operation matrix

Financial and lifecycle restrictions follow the agreed model. Closed-state membership restrictions below are proposed defaults pending confirmation.

| Operation | Open | Closed |
|---|---|---|
| View authorized history and archives | Allowed | Allowed |
| Record/correct/remove expense | Existing permissions, when implemented | Blocked; reopen first |
| Record/confirm/reject payment | Existing payment rules | Blocked |
| Waive debt | Authorized creditor under existing rules | Blocked |
| Close | Admin; all prerequisites satisfied | No new closure/version |
| Reopen | Invalid source state | Admin; reason required |
| Edit archive contents | Never | Never |
| Create archive | Automatically on successful closure | No separate action |
| Invite, accept, leave, remove, promote | Existing rules; admin authority where applicable | Proposed: reopen first |

Still to decide: administrative membership operations while Closed and recovery when no admin is available. Do not silently introduce a participant reopening bypass.

Personal actions such as marking a notification read do not change group finances and need not be blocked.

## 10. Event processing and concurrent changes

The existing ledger processes financial events asynchronously. Consider this unsafe sequence:

1. An expense is saved.
2. Its ledger update is pending, so balances still appear zero.
3. An admin closes based on that outdated zero.
4. An incomplete archive is created.
5. The ledger later books the expense, creating debt in a Closed group.

Required invariant:

> Closure and its archive must account for every accepted financial operation before the closure boundary. Later financial writes must be rejected while the group is Closed.

The implementation must coordinate financial writes, ledger completion, balance validation, and snapshot creation. A group-row lock is insufficient unless relevant writers participate in that coordination. A fixed delay is not proof that processing has finished.

Concurrent closures must produce one closure and one archive. Close and reopen requests must be tied to the appropriate revision so delayed requests cannot affect a later correction cycle.

### Revised role of AllBalancesSettled

`AllBalancesSettled` must **not** close the group or create an archive by itself. It may refresh an eligibility hint or tell the admin that balances reached zero. A subsequent close command still rechecks all conditions.

Archive creation follows the accepted admin close operation. Potential events include group closed, archive created, and group reopened; exact names and payloads must follow an updated domain diagram. These are proposed concepts, not claims about existing APIs.

## 11. User flows

### First closure

1. Members record expenses and payments while Open.
2. Creditors answer payments; ledger processing completes.
3. The UI indicates eligibility when prerequisites hold.
4. An admin selects “Close group.”
5. The app shows the summary and warning.
6. Admin confirms; server revalidates.
7. The app shows Closed and archive v1.

### Correction and new archive

1. Someone identifies an error.
2. Admin selects “Reopen for corrections” and gives a reason.
3. The app shows Open and retains archive v1.
4. An authorized user corrects working records.
5. Resulting debt is resolved under existing financial rules.
6. Admin closes again when eligible.
7. The app shows Closed and archive v2; v1 remains available.

## 12. Acceptance scenarios

| Scenario | Expected result |
|---|---|
| Open group reaches zero | Remains Open; no archive |
| Admin closes an eligible group | Closed; one archive created automatically |
| Ordinary participant attempts close | Refused; no state change or archive |
| Balances are +60/-60 | Closure refused despite total zero |
| Payment reported but unconfirmed | Closure refused |
| Expense saved but not booked | No premature closure |
| Admin cancels confirmation | No changes |
| Archive persistence fails | No successful closure with missing snapshot; explicit failure/recovery |
| Same close request retried | No duplicate archive |
| Two admins close concurrently | One closure/version |
| Old close retry arrives after reopening | Cannot silently close the later revision |
| Participant attempts reopening | Refused |
| Admin omits reopening reason | Refused |
| Admin reopens with reason | Open; archives and payments preserved |
| Correction after reopening creates debt | Reclosure blocked until settled |
| Corrected group closes at zero | New version; old financial snapshot unchanged |
| Reopen/reclose without financial edits | New closure/version with lifecycle history |
| Financial write while Closed | Refused regardless of UI |
| Delayed all-settled event arrives | No automatic closure or archive |
| Admin closes an empty eligible group | First archive created without requiring a payment event |
| Debtor refuses to pay | Group remains Open; admin cannot override zero requirement |

Rejected commands must not leave financial changes, completed archives, or misleading lifecycle events behind.

## 13. Required changes to the original specification

Source: [ShareWise requirements](../domain/ShareWise_Requirements.md), particularly L1–L6, R5, J8, J12, and J13.

| Original concept | Revised rule |
|---|---|
| OPEN → CLOSED_FOR_SETTLEMENT → ARCHIVED | Two group states plus archive versions |
| L1: admins close, reopen, archive | Admins close/reopen; archive creation is part of closure |
| L2: payments allowed while closed | Payments while Open; Closed blocks financial changes |
| L3: reopen before expense corrections | Retain; add reason and preserved archives |
| L4: closing may leave debt | Closing requires every balance to be zero |
| L5: last zero balance archives | Successful admin closure automatically creates an archive |
| L6: ARCHIVED is final | Closed group can reopen; individual archive financial contents remain immutable |
| R5: expense changes only while OPEN | Retain operation-specific permissions after reopening |
| J8: payments in OPEN or CLOSED_FOR_SETTLEMENT | Payments in Open only |
| J12: start settlement and reopen | Close a settled group; reopen for corrections |
| J13: separate archive transition | Automatic archive creation within closure |

The earlier decision to freeze expenses while recalculating settlement suggestions belonged to the removed settlement phase. Here suggestions update while Open; financial facts freeze when Closed.

Enum names, endpoints, events, migrations, and any persisted old statuses must be reviewed. Relabeling `CLOSED_FOR_SETTLEMENT` does not by itself implement these changed semantics.

## 14. Remaining design work

The central lifecycle decisions are confirmed by the user. Remaining work:

1. Align authoritative team requirements and the domain diagram with this revision.
2. Confirm the supporting closure safeguards: no pending payments, completed processing, consistent revision.
3. Specify archive contents, access, historical identities, retention, and optional export.
4. Decide membership/admin operations while Closed.
5. Specify corrections while payments are pending in an Open group.
6. Design concurrency, snapshot persistence, retries, and failure recovery.
7. Define API responses and explanations for closure blockers.
8. Implement and test lifecycle operations, versioning, notifications, history, and UI after design agreement.

Implementation reference points:

- [Group aggregate](../../backend/src/main/java/de/hsh/favs/sharewise/group/domain/Group.java)
- [Group service](../../backend/src/main/java/de/hsh/favs/sharewise/group/application/GroupService.java)
- [Ledger service](../../backend/src/main/java/de/hsh/favs/sharewise/settlement/application/GroupLedgerService.java)
- [Payment service](../../backend/src/main/java/de/hsh/favs/sharewise/settlement/application/SettlementPaymentService.java)
- [AllBalancesSettled](../../backend/src/main/java/de/hsh/favs/sharewise/shared/events/AllBalancesSettled.java)
- [Architecture conventions](../conventions/01_ARCHITECTURE_AND_NAMING.md)

Only this discussion document is updated. It describes intended behavior and remaining design questions, not completed functionality.
