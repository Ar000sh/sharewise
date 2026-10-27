package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate root: the append-only ledger of one group. Every recorded expense books
 * entries (the payer credited what they paid, every share holder debited their share,
 * R14); a member's balance is the sum of their entries. Booking an expense twice is
 * prevented by the service ({@code isBooked}) and, as a last line of defence, by a unique
 * constraint.
 */
@Entity
@Table(name = "settlement_ledger")
public class GroupLedger {

	@Id
	@Column(name = "group_id")
	private UUID groupId;

	@OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<LedgerEntry> entries = new ArrayList<>();

	protected GroupLedger() {
	}

	public GroupLedger(GroupId group) {
		this.groupId = group.value();
	}

	/**
	 * Books the expense: the payer is credited the total, every share holder debited
	 * their share (R14).
	 */
	public void appendExpenseEntry(ExpenseRecorded expense, Instant occurredAt) {
		entries.add(new LedgerEntry(this, expense.payerId(), expense.totalCents(), LedgerEntryType.EXPENSE_PAID,
				expense.expenseId(), occurredAt));
		for (var share : expense.shares()) {
			entries.add(new LedgerEntry(this, share.memberId(), -share.shareCents(), LedgerEntryType.EXPENSE_OWED,
					expense.expenseId(), occurredAt));
		}
	}

	/**
	 * Only members that appear in an entry; the sum is always 0. Loads every entry, see
	 * {@code GroupLedgerRepository#totals}.
	 */
	public Map<MemberId, Long> projectBalances() {
		Map<MemberId, Long> balances = new HashMap<>();
		for (var entry : entries) {
			balances.merge(entry.member(), entry.deltaCents(), Long::sum);
		}
		return balances;
	}

	public List<LedgerEntry> entries() {
		return List.copyOf(entries);
	}

}
