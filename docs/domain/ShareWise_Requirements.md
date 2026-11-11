# ShareWise: Requirements (single source of truth)

**What this file is:** the one place that defines what ShareWise must do: scope, actors, business rules, user journeys and domain events.
- **If another file disagrees with this one, this file wins.** Fix the other file.
- Last updated: **14.10.26** (team workshop).

**Where the content comes from:**
- Lecture: Intro slides 30–34, DDD slides 5–7, 35, 36, 40, 64, 68, 69.
- Our own drafts (the first two stayed in the FAVS study folder, they are history; this file wins over them):
  - `Ex1b_ShareWise_User_Journeys.md` (journeys and market research)
  - `Ex2_ShareWise_Domain_and_Subdomains.md`
  - `Ubiquitous_Language.md` (in this folder, was `Ex2_Ubiquitous_Language.md`)

**Conventions:**
- 🟢 = our own design decision, not lecture content. We must be able to defend it in P1.
- "(slide N)" = a page of the DDD deck, unless "Intro" is named.
- Terms follow the glossary in `Ubiquitous_Language.md`. Examples: **correct**, never "edit"; **remove**, never "delete"; **settlement payment**, never just "payment".

---

## 1. The product

ShareWise is "a collaborative application for managing expenses within groups". Tagline: *"Shared expenses made simple"* (Intro slide 31).

**It helps groups to** (Intro slide 31):
1. record shared expenses
2. distribute costs among participants
3. understand who owes whom
4. record settlement payments
5. close and archive completed groups

**Our differentiator** 🟢: an explicit **close-for-settlement** phase. While a group is closed, expenses are frozen and only settlement payments are allowed. No competitor has this; they only archive (see Ex1b market research).

**What ShareWise does *not* do** 🟢 (team decision 14.10):
- **No multi-currency.** Each group has exactly one currency, set when the group is created. There are no exchange rates and no conversion.
- **No real money transfer.** ShareWise only *records* settlement payments. The money moves outside the app (cash, bank, PayPal).

---

## 2. Scope (MoSCoW)

The MVP is small but rich in domain logic: the interesting rules sit in splitting, correcting and closing.

| Priority | Feature | Journey | Rules |
|---|---|---|---|
| **Must** | Register and log in | J1 | – |
| **Must** | Create a group (name, currency) | J2 | – |
| **Must** | Invite by link or email, join, decline | J3, J9 | R9, R10 |
| **Must** | Record an expense: split equal / exact / percentage | J4 | R1–R5 |
| **Must** | Correct an expense, keeping its history | J5 | R5–R7 |
| **Must** | Remove an expense (soft delete) | J6 | R5, R6, R8 |
| **Must** | Balances + simplified settlement plan | J7 | R14, R15 |
| **Must** | Record a settlement payment | J8 | R16, R17 |
| **Must** | Leave a group / remove a member, only at balance 0 | J10, J11 | R11–R13 |
| **Must** | Promote a member to admin | J10 | R12 |
| **Must** | Close a group for settlement, reopen it | J12 | L1–L6 |
| **Must** | Archive a group (automatically or manually) | J13 | L4, L5 |
| Should | Creditor confirms a settlement payment (the MVP may skip this) | J8 | R16 |
| Should | Activity feed | J16 | – |
| Should | Notifications (invite, expense recorded/corrected, group closed, settlement payment) | several | – |
| Should | Payment reminders | J14 | – |
| Could | Placeholder members without an account | J17 | conflicts with R9 |
| Could | Recurring expenses | J15 | – |
| Could | Categories + statistics | – | – |
| Could | Comments on expenses | – | – |
| Could | CSV/PDF export of an archived group | J13 | – |
| Could | Restore a removed expense | J6 | – |
| Won't | Multi-currency / exchange rates · real payment transfer · receipt OCR · multiple payers per expense · offline mode | – | – |

---

## 3. Actors and roles

DDD slide 40: the **same person** plays a different role in each context. That is the basis for our bounded contexts.

| Actor | Who it is | Context |
|---|---|---|
| **Visitor** | Not registered yet. Can open an invite link and is asked to register | Identity |
| **User** | Has an account (email, display name, password). Can create groups | Identity |
| **Member** | A user who joined a group | Group |
| **Admin** | A member with extra rights: close, reopen, archive, remove members, promote. The creator is the first admin | Group |
| **Former member** | A member who left or was removed. Still shown in old expenses | Group |
| **Payer** | The member who paid one specific expense | Expense |
| **Participant** | A member who shares the cost of one specific expense | Expense |
| **Recorder** | The member who recorded an expense | Expense |
| **Debtor / Creditor** | A member with a negative / positive balance | Settlement |
| **System** | Timers (invitation expiry, auto-archive) and notifications | – |

---

## 4. Business rules

### 4.1 Group lifecycle (answers the open questions on DDD slide 7)

```
          closeGroupForSettlement()            all balances = 0 (auto)
  OPEN ───────────────────────────► CLOSED_FOR_SETTLEMENT ───────────────────► ARCHIVED
    ▲                                   │                    or archiveGroup() by an admin
    └──────── reopenGroup() ────────────┘                    (only if all balances = 0)
```

| # | Rule 🟢 | Slide-7 question it answers |
|---|---|---|
| **L1** | Only **admins** may close a group for settlement, reopen it or archive it. | Who is authorized? |
| **L2** | While the group is closed, **no expenses can be recorded**. Only settlement payments are allowed. | Can expenses be added afterwards? |
| **L3** | While the group is closed, **no expenses can be corrected or removed**. An admin must reopen the group first. The reopen shows in the activity feed. | Can they still be corrected? |
| **L4** | Closing does **not** require balances of 0. Closing freezes the expenses and fixes the final settlement plan. **Archiving requires all balances = 0.** | Must all balances be 0? |
| **L5** | The group is **archived automatically** as soon as the last balance reaches 0. An admin may also archive manually, but only when all balances are 0. | Is archiving automatic? |
| **L6** | An admin can **reopen** a closed group. **ARCHIVED is final** and read-only. | Can closing be revoked? |

### 4.2 Expenses (Expense context)

| # | Rule | Source |
|---|---|---|
| **R1** | The amount of an expense is positive. | slide 69 |
| **R2** | The payer is a member of the group at the moment the expense is recorded. | slide 69 |
| **R3** | Every participant is a member of the group. An expense has at least 1 participant. The payer may or may not be a participant (the recorder must be one, see R18). ⚠ Slide 64 says 2..\*, see §8 | slide 69 + 🟢 |
| **R4** | The shares add up exactly to the amount. With a percentage split, the percentages add up to 100 %. Remainder cents go to the first participant(s). | slide 69 + 🟢 rounding |
| **R5** | Expenses can only be recorded, corrected or removed while the group is OPEN. | 🟢 |
| **R6** | The recorder, the payer or an admin may correct or remove an expense. | 🟢 |
| **R7** | A correction never overwrites silently. The old version stays in the history, the status becomes CORRECTED, and the participants are notified. | slide 69: "a finalized expense may not be changed directly" |
| **R8** | Removing an expense is a soft delete. The status becomes REMOVED, the expense no longer counts for balances, and it stays visible in the history. | 🟢 |
| **R18** | The member who records an expense is always one of its participants. (Alice cannot record "I paid for Bob and Carol" for herself; Bob records it with Alice as the payer.) Enforced in the form and in `Expense.record` (`RECORDER_NOT_PARTICIPANT`, 422). | 🟢 decided 10.11.26 |

**Split strategies:**
- **Equal:** amount ÷ number of participants, with remainder cents per R4.
- **Exact:** an amount per participant. The amounts must add up to the total.
- **Percentage:** a percentage per participant. The percentages must add up to 100 %.

**Expense status:** ACTIVE → CORRECTED / REMOVED (slide 64).

### 4.3 Membership (Group context)

| # | Rule 🟢 |
|---|---|
| **R9** | A person joins a group only through a valid invitation (link or email). An invitation expires after **7 days**. Status: PENDING / ACCEPTED / DECLINED / EXPIRED. |
| **R10** | **Late joiners** share only in expenses recorded *after* they join. Nothing is retroactive, unless an existing expense is explicitly corrected to include them. |
| **R11** | A member can **leave** only at **balance 0**. The same applies when an admin **removes** a member. This is the industry standard (Splitwise). |
| **R12** | The **last admin** can't leave while other members remain. They must promote someone first. |
| **R13** | Former members stay visible in old expenses, shown as "Bob (left)". |

A group always has a name, which must not be empty, and exactly one currency.

### 4.4 Balances and settlement (Settlement context)

| # | Rule |
|---|---|
| **R14** | Balance = total paid − total owed shares (over active expenses only) ± confirmed settlement payments. **The sum of all balances in a group is always 0.** |
| **R15** | The settlement plan uses the **minimum number of payments**. It **never changes anyone's balance**, only who pays whom. (Adopted from Splitwise's "simplify debts".) |
| **R16** 🟢 | The debtor records a settlement payment (PENDING). The creditor confirms it (CONFIRMED) or rejects it (REJECTED). **Only confirmed payments change balances.** (Should; the MVP may skip confirmation.) |
| **R17** 🟢 | A settlement payment can't be larger than the debtor's open debt. Partial payments are allowed. |

**Terms that are easy to mix up:**
- **Balance** = a member's *net* position (+ is owed, − owes). Example: Alice +371.
- **Debt** = one directed amount in the plan. Example: David → Alice €371.

---

## 5. User journeys

Each journey lists: actor · trigger · preconditions · main flow · alternatives/errors · rules · **domain events**.

### J1: Register and log in
- **Actor:** Visitor
- **Trigger:** wants to use ShareWise, or opened an invite link
- **Flow:**
  1. Enter email, display name and password.
  2. Confirm the email.
  3. Log in.
  4. Land on "My groups" (empty).
- **Alternatives:**
  - The email is already registered → offer login.
  - Came from an invite link → after login, continue straight to J3.
- **Events:** `UserRegistered`
- *(Identity is generic: Keycloak / Spring Security, not built ourselves.)*

### J2: Create a group
- **Actor:** User (Alice)
- **Flow:**
  1. Choose "New group".
  2. Enter the name "Mallorca 2027" and the currency EUR (fixed for the group). Description and dates are optional.
  3. The group is created in status OPEN.
  4. Alice becomes a member and an admin.
- **Errors:** empty name.
- **Events:** `GroupCreated`, `MemberJoined(Alice, ADMIN)`

### J3: Invite and join
- **Actors:** Member (Alice) → invitee (Bob, Carol, David)
- **Preconditions:** group OPEN
- **Flow:**
  1. Alice opens the group and chooses "Invite".
  2. She shares an invite **link** or enters **emails**.
  3. Bob opens the link and logs in (or registers first, J1).
  4. Bob sees a group preview and chooses "Join".
  5. Bob becomes a member, and everyone sees "Bob joined".
- **Alternatives:**
  - The invitation has expired → "Ask for a new invite".
  - Already a member → the group opens.
  - Group CLOSED or ARCHIVED → joining is not possible.
  - The invitee declines → status DECLINED.
- **Rules:** R9, R10
- **Events:** `MemberInvited`, `InvitationAccepted` / `InvitationDeclined`, `MemberJoined`

### J4: Record an expense
- **Actor:** Member
- **Preconditions:** group OPEN
- **Flow:**
  1. Choose "Add expense".
  2. Enter title, amount, date, payer (default: me) and participants (default: everyone).
  3. Choose the split: **equal / exact / percentage**.
  4. The system previews each person's share.
  5. Save. The balances update and the participants are notified.
- **Variants:**
  - **4a Equal:** €180 / 4 = €45 each.
  - **4b Exact:** an amount per person; the amounts must add up to the total (R4).
  - **4c Percentage:** the percentages must add up to 100 %; rounding per R4.
- **Errors:**
  - amount ≤ 0
  - shares ≠ total
  - payer or participant is not a member
  - group not OPEN
- **Rules:** R1–R5
- **Events:** `ExpenseRecorded`

### J5: Correct an expense
- **Actor:** recorder, payer or admin (R6)
- **Trigger:** wrong amount, wrong payer, or someone missing from the split
- **Flow:**
  1. Open the expense and choose "Correct".
  2. Change the amount, payer, participants or split.
  3. Optionally give a reason.
  4. Save. The old version goes to the history, and the status becomes CORRECTED.
  5. The balances are recalculated and the affected participants are notified.
- **Alternatives:**
  - Group CLOSED → "Ask an admin to reopen the group" (→ J12b).
  - Not authorized → error.
- **Rules:** R5–R7
- **Events:** `ExpenseCorrected(old, new)`
- *Example:* the dinner was actually €200, not €180, so each share goes from €45 to €50.

### J6: Remove an expense
- **Actor:** recorder, payer or admin
- **Flow:**
  1. Open the expense and choose "Remove".
  2. Confirm.
  3. The status becomes REMOVED. The expense no longer counts for balances but stays visible in the history.
- **Alternative:** restore a removed expense (Could).
- **Rules:** R5, R6, R8
- **Events:** `ExpenseRemoved`
- *Example:* the groceries were entered twice, so the duplicate is removed.

### J7: Review balances ("who owes whom")
- **Actor:** Member
- **Flow:**
  1. Open the "Balances" tab.
  2. See each member's balance (+ is owed / − owes).
  3. See the **simplified settlement plan**, e.g. "David → Alice €371".
  4. Optionally drill down into the expenses behind a balance.
- **Rules:** R14, R15
- **Events:** none (this is a query; a read-model / CQRS candidate later)

### J8: Record a settlement payment
- **Actors:** Debtor (David) → Creditor (Alice)
- **Preconditions:** group OPEN or CLOSED_FOR_SETTLEMENT
- **Flow:**
  1. David chooses "Settle up" next to "David → Alice €371".
  2. The amount is prefilled. He records it as paid; the money itself moved outside the app.
  3. The settlement payment is PENDING, and Alice is notified.
  4. Alice confirms receipt. The status becomes CONFIRMED and the balances update.
- **Alternatives:**
  - Partial payment (e.g. €200 of €371).
  - Alice rejects it → REJECTED, and David is notified.
  - Amount > open debt → error (R17).
- **Rules:** R16, R17
- **Events:** `SettlementPaymentRecorded`, `SettlementPaymentConfirmed` / `SettlementPaymentRejected`

### J9: Late joiner
- **Actor:** Eve (invited on day 3)
- **Flow:** same as J3. From then on, Eve can be the payer or a participant in *new* expenses.
- **Rule:** R10. The hotel (day 1) is not split with Eve, unless someone explicitly corrects it (J5).
- **Events:** `MemberJoined`

### J10: Leave a group
- **Actor:** Member (Bob)
- **Flow:**
  1. Choose "Leave group".
  2. The system checks Bob's balance.
  3. If it is 0, Bob leaves. He becomes a FORMER_MEMBER and stays visible in old expenses.
- **Alternative:** balance ≠ 0 → "You still owe Alice €202. Settle first." (with a link to J8).
- **Error:** Bob is the last admin and other members remain → "Promote another admin first" (R12).
- **Rules:** R11–R13
- **Events:** `MemberLeft`, (`MemberPromoted` beforehand if needed)

### J11: Remove a member (admin)
- **Actor:** Admin
- **Flow:**
  1. Open the member list and choose "Remove Bob".
  2. The system checks Bob's balance (R11).
  3. Bob is removed and notified.
- **Alternative:** balance ≠ 0 → the admin is shown the settlement options.
- **Rules:** R11, R13
- **Events:** `MemberRemoved`

### J12: Close the group for settlement (+ J12b reopen)
- **Actor:** Admin (Alice)
- **Preconditions:** group OPEN
- **Flow:**
  1. Choose "Close group for settlement".
  2. The system shows a summary: total spent, final balances, simplified plan.
  3. Alice confirms. The status becomes CLOSED_FOR_SETTLEMENT.
  4. Recording, correcting and removing expenses is now blocked.
  5. All members are notified with "your final amount".
- **J12b Reopen:**
  1. The admin chooses "Reopen". The status becomes OPEN.
  2. Expenses can be recorded and corrected again.
  3. The members are notified.
- **Rules:** L1–L4, L6
- **Events:** `GroupClosedForSettlement`, `GroupReopened`

### J13: Archive the group
- **Trigger:** the last settlement payment is confirmed, so all balances are 0 (automatic). Or an admin archives manually.
- **Flow:**
  1. The status becomes ARCHIVED.
  2. The group is read-only and moves to "Archived groups".
  3. Optional: export (Could).
- **Error:** manual archive while balances ≠ 0 → refused.
- **Rules:** L4–L6
- **Events:** `AllBalancesSettled`, `GroupArchived`

### Should / Could journeys (short)
- **J14 Payment reminder** (Should): a creditor sends a reminder to a debtor. Event: `PaymentReminderSent`.
- **J15 Recurring expense** (Could): the system generates a monthly expense, e.g. rent in a shared flat. Event: `RecurringExpenseScheduled`.
- **J16 Activity feed** (Should): a chronological list of everything that happened in a group. It is just the domain events rendered as a feed.
- **J17 Placeholder member** (Could): an admin adds "Grandpa" without an account. He can claim the placeholder later when he registers. ⚠ Conflicts with R9.

---

## 6. Reference example: "Mallorca 2027"

Use this example in the presentation, in tests and in the glossary.
- **Members:** Alice (admin), Bob, Carol, David. **Eve joins on day 3.**
- Splits are equal unless stated otherwise. The currency is EUR.

| # | Day | Journey | What happens | Payer | Amount | Participants → share |
|---|---|---|---|---|---|---|
| 1 | 0 | J1–J3 | Alice registers, creates the group and invites the others. Bob, Carol and David join | | | |
| 2 | 1 | J4a | **Hotel** | Alice | 900 | A, B, C, D → 225 |
| 3 | 1 | J4a | **Dinner** (entered as €180) | Bob | 180 | A, B, C, D → 45 |
| 4 | 2 | J5 | The dinner is **corrected** to €200 (the receipt was wrong) | Bob | 200 | A, B, C, D → 50 |
| 5 | 2 | J4a | **Rental car** | Carol | 420 | A, C, D → 140 |
| 6 | 2 | J4a | **Museum** | David | 75 | B, C, D → 25 |
| 7 | 3 | J9 | **Eve joins** (late joiner) | | | |
| 8 | 3 | J4a | **Boat trip** | Eve | 300 | A, B, E → 100 |
| 9 | 4 | J4a | **Groceries** | Bob | 60 | A, B, C, D, E → 12 |
| 10 | 4 | J6 | The groceries were entered **twice**; the duplicate is **removed** | | | |
| 11 | 5 | J4b | **Farewell dinner, exact split** | Alice | 250 | A 50, B 50, C 40, D 60, E 50 |

**Balances after step 11** (verified by script, 14.10):

| Member | Paid | Owed shares | **Balance** |
|---|---|---|---|
| Alice | 1150 | 577 | **+573** |
| Bob | 260 | 462 | **−202** |
| Carol | 420 | 492 | **−72** |
| David | 75 | 512 | **−437** |
| Eve | 300 | 162 | **+138** |
| **Σ** | **2205** | **2205** | **0** ✓ |

**How it continues:**

| # | Day | Journey | What happens |
|---|---|---|---|
| 12 | 5 | J10 fails | Bob tries to leave and is refused: "You owe €202". |
| 13 | 5 | J8 | Bob pays Alice €202, and Alice confirms. Bob = 0, Alice = +371. |
| 14 | 5 | J10 | Bob leaves. He appears as "Bob (left)" in old expenses. |
| 15 | 6 | J12 | Alice closes the group for settlement. The final balances and the plan are shown. |
| 16 | 6 | J4 fails | Carol tries to add a forgotten taxi and is refused: "Group is closed for settlement". (The alternative is J12b: reopen, add the taxi, close again.) |
| 17 | 7–10 | J8 | Settlement payments per the plan, each confirmed by the creditor. |
| 18 | 10 | J13 | The last confirmation brings all balances to 0, and the group is **auto-archived**. |

**Simplified settlement plan after Bob leaves** (Alice +371, Eve +138, Carol −72, David −437). It takes 3 payments instead of up to 6:
- David → Alice **€371**
- David → Eve **€66**
- Carol → Eve **€72**

After these payments, everyone's balance is 0 (verified by script).

---

## 7. Domain events (overview for Ex3)

| Event | Published by (context) | Raised in journey | Who reacts |
|---|---|---|---|
| `UserRegistered` | Identity | J1 | – |
| `GroupCreated` | Group | J2 | Activity |
| `MemberInvited`, `InvitationAccepted`, `InvitationDeclined` | Group | J3 | Notification, Activity |
| `MemberJoined`, `MemberLeft`, `MemberRemoved`, `MemberPromoted` | Group | J2, J3, J9–J11 | Expense, Settlement (who is a member), Activity, Notification |
| `ExpenseRecorded`, `ExpenseCorrected`, `ExpenseRemoved` | Expense | J4–J6 | **Settlement** (recalculate balances), Activity, Notification |
| `SettlementPaymentRecorded`, `…Confirmed`, `…Rejected` | Settlement | J8 | Notification, Activity |
| `AllBalancesSettled` | Settlement | J13 | **Group** (auto-archive) |
| `GroupClosedForSettlement`, `GroupReopened` | Group | J12 | **Expense** (block or unblock expenses), Settlement (final balances), Notification, Activity |
| `GroupArchived` | Group | J13 | Activity, Notification |
| `PaymentReminderSent` (Should) | Settlement | J14 | Notification |

---

## 8. Still open (decide before P1, 20.10)

**Conflicts on the slides:**
- [ ] **ExpenseShare (VO, slide 68) vs ExpenseParticipant (entity, slide 64).** Pick one name and one type. Also put it in the glossary.
- [ ] **2..\* participants (slide 64) vs our R3 (≥ 1).** Adopt 2..\* or justify the difference.
- [ ] **UserId (slide 64) vs MemberId (slide 40 logic).** Which ID does the Expense context store?
- [ ] **How to check "payer belongs to the group"** (slide 69), when Expense only holds a GroupId (slide 67)? Either the application service checks it before the command, or Expense keeps a local membership copy fed by events.

**Domain questions:**
- [ ] **Correction after a settlement payment:** Bob already paid €202, then the hotel is corrected. Does Bob get a new debt or credit, and is he notified again?
- [ ] **Who may invite:** any member, or admins only?
- [ ] **Rejoining:** can a former member be invited again, and do they keep their history?
- [ ] **Auto-archive scope:** only from CLOSED_FOR_SETTLEMENT, or also an OPEN group whose balances happen to be 0?
- [ ] **Deleting a group:** allowed for an OPEN group without expenses?
- [ ] **Account deletion (GDPR):** the user still appears in a group's history.
- [ ] **Placeholder members (J17) vs R9:** make an exception, or drop J17?
- [ ] **Rejected settlement payment:** edit and resubmit it, or record a new one?
- [ ] **Plan recalculation:** after partial payments or a reopen, is the plan recomputed from scratch?
- [ ] **Is the payer automatically a participant?** Current answer: no. The recorder, however, always is one (R18).

---

## 9. Change log

| Date | Change |
|---|---|
| 14.10.26 | File created, merging Ex1b + Ex2 drafts. **Dropped:** multi-currency / exchange rates and real payment transfer (team workshop). |
