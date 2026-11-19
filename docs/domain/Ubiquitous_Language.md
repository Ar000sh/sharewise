# ShareWise Ubiquitous Language (Ex2 part 4, DDD slides 29–36, 41)

**Status:** 🟢 first proposal for workshop step 5. It is scoped to the 6 contexts of the workshop and uses the context map of the workshop. *(Moved into this repository on 12.11.26, from the FAVS study folder where it was `work/Ex2_Ubiquitous_Language.md`. The workshop files it was written from, `prep/P1_Team_Workshop_Plan.md`, `work/Ex2_Subdomains_vs_Bounded_Contexts.md` and `work/Ex2_Context_Map.md`, stayed in that folder. The code's names are decided by the domain diagram and `docs/conventions/01` section 3; where this glossary and the diagram differ, the diagram wins.)*
**Slide 36 asks for:** the terms Expense, Share, Balance, Debt, Settlement + **business rules (invariants)** + a **Glossary**.

---

## 1. What the ubiquitous language is (and why)

**The problem (slides 29, 30, 32):** classic development is a chain of translations:
domain knowledge → analysis model → requirements → system design → source code.
- "Translation errors can occur at any of these transitions." Domain knowledge gets lost on the way to the developers.

**DDD's answer (slides 31, 32):** **one** language shared by domain experts, developers and the code. It sits in the *centre* (slide 32 circle), not at the end of a chain.
- **Ubiquitous** means:
  - everyone in the project speaks and understands it
  - all relevant concepts are described in it
  - it evolves over time
  - it is present in every phase, **including the code** (slide 31)
- It uses **business terms only**, no technical jargon (slide 32, 34).
- **One meaning per term** (slide 33).
- "The clarity of the language shows how deeply the problem space is understood. **Unclear terms point to hidden concepts. Make implicit assumptions explicit**" (slide 31).

**But not one language for everything (slides 39, 41):** a global, company-wide language usually fails ("Lead" = an event in Marketing, a whole process in Sales). So we build **several smaller languages, one per bounded context**. The same word may mean different things in different contexts, but never two things inside one.

**How the 4 parts of Ex2 connect:**
- Unclear words → they reveal **context boundaries** (part 2).
- At each boundary, the context map decides **whose language is used** (part 3).
- Inside each context, the glossary fixes the meaning (part 4, this file).
- The glossary terms become **class and method names** (slide 35 → Ex3).

---

## 2. Method we used
1. Collected every noun and verb from the journeys J1–J17 and slides 31, 33, 36, 40, 64, 68, 69.
2. Flagged **ambiguous terms** (one word, several meanings) and **synonyms** (several words, possibly different concepts) (slide 33). Resolved each one (§3).
3. Assigned every term to **one bounded context** and wrote a definition + a Mallorca example (§4).
4. Wrote the rules as **business sentences** (slide 34 "like so, not like") (§5).
5. Named the operations in domain language (slide 35) (§6).

---

## 3. Ambiguous terms and synonyms we resolved (slide 33)

Same technique as slide 33's "policy" → "regulatory provision" / "insurance contract".

| Word heard in discussion | Problem | 🟢 Resolution: use these terms |
|---|---|---|
| **"user"** | One word for 4 different roles (slide 40) | **User** (Identity) · **Member** (Group) · **Payer / Participant** (Expense) · **Debtor / Creditor** (Settlement) |
| **"settlement"** | Three meanings: the phase, the plan, a single payment | **Settlement phase** = group status CLOSED_FOR_SETTLEMENT · **Settlement plan** = the list of suggested transfers · **Settlement payment** = one recorded transfer. Never just "settlement" alone |
| **"payment"** | A settlement payment or the payer's original outlay | **Settlement payment** (recorded, between members; the money itself moves outside the app) · **Payer** paid an **expense** |
| **"balance" vs "debt"** (both on slide 36) | Used as synonyms in everyday speech | **Balance** = a member's *net* position in a group (signed: + is owed, − owes). **Debt** = one directed amount *from one debtor to one creditor* in the settlement plan. Alice has a balance of +371; David has a debt of €371 to Alice |
| **"share"** vs **"participant"** (slides 68 vs 64) | Slide 68: ExpenseShare VO. Slide 64: ExpenseParticipant entity | **Participant** = a member taking part in one expense. **Share** = the amount a participant owes for that expense. (Whether this becomes a VO or an entity is decided in Ex3.) |
| **"edit" / "change" / "update" an expense** | Hides that the old version is kept | **Correct** (keeps history, status CORRECTED). We never say "edit" or "update" |
| **"delete"** | Suggests the data is gone | **Remove** an expense (soft, status REMOVED). **Remove** a member (by an admin). **Leave** (by the member themselves) |
| **"close"** vs **"archive"** | Often used as synonyms | **Close for settlement** = freeze expenses, settlements only, can be reopened. **Archive** = final and read-only, only at all balances 0 |
| **"owner" / "creator" / "admin"** | Synonyms? | **Admin** = a role with rights to close, reopen, remove members and promote. The **creator** is simply the first admin; "owner" is not used |
| **"group"** | Means different things per context | Group context: the group with members, roles and lifecycle. Expense / Settlement: only a **GroupId** that scopes expenses and balances |
| **"invite"** | Action vs object | **Invite** (verb, by a member) creates an **Invitation** (object with status PENDING / ACCEPTED / DECLINED / EXPIRED) |

---

## 4. Glossary per bounded context

### 4.1 Group context (supporting)
| Term | Definition | Mallorca example |
|---|---|---|
| **Group** | A set of members who share expenses for a common purpose. Has a name, one currency and a status | "Mallorca 2027", EUR |
| **Group name** | The name shown to members; must not be empty | "Mallorca 2027" |
| **Member** | A user who belongs to a group | Alice, Carol, David, Eve |
| **Admin** | A member allowed to close, reopen, archive, remove members and promote others. Every group has at least one | Alice (creator) |
| **Former member** | A member who left or was removed; still shown in old expenses | "Bob (left)" |
| **Invitation** | An offer to join one group, valid for 7 days. Status PENDING / ACCEPTED / DECLINED / EXPIRED | Alice invites Eve on day 3 |
| **Join** | Accepting a valid invitation; from then on the member may take part in *new* expenses | Eve joins on day 3 |
| **Late joiner** | A member who joined after expenses were recorded; not part of earlier expenses | Eve is not part of the hotel |
| **Leave** | A member ends their own membership; only at balance 0 | Bob leaves on day 5 after paying €202 |
| **Remove member** | An admin ends someone else's membership; only at balance 0 | – |
| **Promote** | An admin gives the admin role to another member | Required before the last admin leaves |
| **Group status** | OPEN → CLOSED_FOR_SETTLEMENT → ARCHIVED | – |
| **Open** | Status in which expenses can be recorded, corrected and removed | Days 0–5 |
| **Close for settlement** | Admin freezes the group: no new or corrected expenses, only settlement payments. Balances need not be 0 | Alice on day 6 |
| **Reopen** | Admin returns a closed group to OPEN | (would be needed for Carol's taxi) |
| **Archive** | Final, read-only status. Automatic when all balances reach 0, or manual by an admin at balance 0 | Day 10 |

### 4.2 Expense context (core)
| Term | Definition | Mallorca example |
|---|---|---|
| **Expense** | An amount one member paid on behalf of some members of the group | Hotel €900 |
| **Amount** | The total of an expense; always positive | €900 |
| **Payer** | The member who paid the expense | Alice for the hotel |
| **Participant** | A member who takes part in (shares the cost of) one expense. The payer may or may not be a participant | Hotel: Alice, Bob, Carol, David |
| **Share** | The part of the amount one participant owes for one expense | €225 each for the hotel |
| **Split** | How the amount is divided among participants. One of: **equal**, **exact amounts**, **percentage** | Farewell dinner: exact (A 50, B 50, C 40, D 60, E 50) |
| **Equal split** | Amount ÷ number of participants; remainder cents go to the first participant(s) | Dinner €200 / 4 = €50 |
| **Exact split** | Each share given as an amount; the shares must add up to the amount | Farewell dinner €250 |
| **Percentage split** | Each share given as a percentage; the percentages must add up to 100 % | – |
| **Record (an expense)** | Enter a new expense in an open group | Bob records the dinner |
| **Correct (an expense)** | Replace the content of an expense; the previous version stays in the history and the status becomes CORRECTED | Dinner €180 → €200 |
| **Remove (an expense)** | Soft delete: status REMOVED; it no longer counts for balances but stays visible | Duplicate groceries |
| **Expense status** | ACTIVE, CORRECTED or REMOVED | – |
| **Expense history** | All previous versions of a corrected expense | Dinner v1 €180, v2 €200 |
| **Recorder** | The member who recorded the expense; may correct or remove it (along with the payer and admins) | – |

### 4.3 Settlement context (core)
| Term | Definition | Mallorca example |
|---|---|---|
| **Balance** | A member's net position in a group: total paid − total shares (active expenses) ± confirmed settlement payments. **The sum of all balances is always 0** | Alice +573, Bob −202, Carol −72, David −437, Eve +138 |
| **Creditor** | A member with a positive balance (is owed money) | Alice, Eve |
| **Debtor** | A member with a negative balance (owes money) | David, Carol |
| **Settled** | A member whose balance is exactly 0 | Bob after paying €202 |
| **Debt** | One directed amount a debtor owes a creditor in the settlement plan | David → Alice €371 |
| **Settlement plan** | The suggested set of debts that brings all balances to 0 with the **minimum number of payments**. It **never changes anyone's balance, only who pays whom** | David → Alice €371, David → Eve €66, Carol → Eve €72 |
| **Settlement payment** | A transfer between two members recorded in ShareWise (the money moves outside the app). Status PENDING → CONFIRMED / REJECTED | Bob pays Alice €202 |
| **Record (a settlement payment)** | The debtor enters a payment they made; it is PENDING | Bob records €202 |
| **Confirm / reject** | The creditor accepts or disputes a pending payment; only confirmed payments change balances | Alice confirms Bob's €202 |
| **Partial payment** | A settlement payment smaller than the debt | David pays €200 of €371 |
| **Final balances** | The balances at the moment the group is closed for settlement | Shown to everyone on day 6 |
| **All balances settled** | Every balance in the group is 0; triggers archiving | Day 10 |

### 4.4 Identity context (generic, external)
| Term | Definition |
|---|---|
| **Visitor** | Someone without an account (e.g. opened an invite link) |
| **User** | A person with an account who can log in. Knows nothing about groups or money |
| **Register / log in** | Create an account / authenticate (handled by Keycloak) |
| **Display name** | The name other members see ("Alice") |

### 4.5 Activity and Notification contexts
| Term | Context | Definition |
|---|---|---|
| **Activity feed** | Activity | Chronological list of what happened in a group, built from domain events ("Alice recorded Hotel €900") |
| **Activity entry** | Activity | One line in the feed; read-only |
| **Notification** | Notification | A message sent to a member about something that concerns them ("Bob paid you €202, please confirm") |
| **Reminder** *(Should)* | Notification | A creditor asks a debtor to pay |

### 4.6 Shared kernel (Expense ∩ Settlement, context map #4b)
| Term | Definition |
|---|---|
| **Money** | An amount + a currency. Calculated in cents, never as a floating-point number |
| **Currency** | ISO currency code. Each group has exactly one currency (EUR), set at creation; all expenses and settlement payments use it. No conversion |
| **GroupId / MemberId** | Identity references to a group / member in the Group context (aggregate rule 3: reference by ID only) |

---

## 5. Business rules (invariants) in domain language

Written the way slide 34 asks for (domain language), checked against slide 69.

| Like so (our rules) | But not like (technical) |
|---|---|
| The amount of an expense is positive. (slide 69) | `amount > 0` in the expenses table |
| The shares of an expense add up to its amount. (slide 69) | `SUM(share.amount) = expense.amount` |
| The payer is a member of the group. (slide 69) | `payer_id` exists in `group_members` |
| All participants are members of the group. (slide 69) | foreign key check on `participant.user_id` |
| A corrected expense keeps its previous version; it is never overwritten. (slide 69: "a finalized expense may not be changed directly") | `UPDATE expense SET …` + an audit row |
| Expenses can only be recorded, corrected or removed while the group is open. | check `status == 0` before the insert |
| A late joiner only takes part in expenses recorded after joining. | filter by `created_at > joined_at` |
| A member can only leave, or be removed, when their balance is 0. | `if balance == 0 then delete membership` |
| The last admin cannot leave while other members remain. | `count(role='ADMIN') > 1` |
| Only an admin can close a group for settlement, reopen it, or archive it. | role check in the controller |
| Once a group is closed, only settlement payments may be recorded. | – |
| A group is archived as soon as all balances are 0; an archived group can never change again. | – |
| The sum of all balances in a group is always 0. | – |
| The settlement plan never changes anyone's balance; it only decides who pays whom. | – |
| A settlement payment counts only after the creditor has confirmed it. | `status = 'CONFIRMED'` filter |
| A settlement payment can't be larger than the debtor's open debt. | – |
| An invitation is valid for 7 days. | `expires_at = now() + 7d` |

---

## 6. Operations in domain language (slide 35 style)

| Context | Like so | But not like | Domain event |
|---|---|---|---|
| Group | `createGroup()` | `insertGroup()` | GroupCreated |
| Group | `inviteMember()`, `acceptInvitation()`, `declineInvitation()` | `addRow()` | MemberInvited, MemberJoined, InvitationDeclined |
| Group | `leaveGroup()`, `removeMember()`, `promoteToAdmin()` | `deleteUser()`, `setRole()` | MemberLeft, MemberRemoved, MemberPromoted |
| Group | **`closeGroupForSettlement()`** (slide 35), `reopenGroup()`, **`archiveGroup()`** (slide 35) | **`updateStatus()`** (slide 35) | GroupClosedForSettlement, GroupReopened, GroupArchived |
| Expense | `recordExpense()`, `correctExpense()`, `removeExpense()` | `saveExpense()`, `update()`, `delete()` | ExpenseRecorded, ExpenseCorrected, ExpenseRemoved |
| Settlement | **`calculateFinalBalances()`** (slide 35), `suggestSettlementPlan()` | **`calculate()`** (slide 35), `processData()` | – (query) |
| Settlement | **`recordSettlementPayment()`** (slide 35), `confirmSettlementPayment()`, `rejectSettlementPayment()` | **`handlePayment()`** (slide 35) | SettlementPaymentRecorded, …Confirmed, …Rejected, AllBalancesSettled |

(note:) Slide 35 spells it `archieveGroup()`, which is a typo. Use `archiveGroup()`.

---

## 7. Open decisions for the workshop
- [ ] **Share as VO or Participant as entity** (slide 68 vs 64): the glossary uses both words with different meanings. Confirm or merge in Ex3.
- [ ] **Is the payer automatically a participant?** Currently no (the payer may be excluded).
- [ ] **"Settled" vs "Archived":** is "settled" a status of the group or only of a member? Currently a member property only.
- [ ] Do we keep **"Recorder"** as its own term, or just say "the member who recorded it"?
