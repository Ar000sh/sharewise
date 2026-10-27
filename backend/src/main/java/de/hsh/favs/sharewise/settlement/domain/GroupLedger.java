package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The append-only ledger of one group; aggregate root of the settlement context (core
 * subdomain). Every recorded expense books entries (the payer credited what they paid,
 * every participant debited their share, R14); a person's balance is the sum of their
 * entries, and the balances of a group always add up to zero. Booking an expense twice is
 * prevented by the service and, as a last line of defence, by a unique constraint.
 */
@Entity
@Table(name = "settlement_ledgers")
public class GroupLedger {

	@Id
	@Column(name = "group_id")
	private UUID groupId;

	@OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<LedgerEntry> entries = new ArrayList<>();

	protected GroupLedger() {
	}

	public GroupLedger(GroupId groupId) {
		this.groupId = groupId.value();
	}

	/**
	 * Books the expense: the payer is credited the total, every participant debited their
	 * share (R14).
	 */
	public void appendExpenseEntry(ExpenseRecorded expense, Instant occurredAt) {
		entries.add(new LedgerEntry(this, expense.paidBy(), expense.totalAmount(), LedgerEntryType.EXPENSE_PAID,
				expense.expenseId().value(), occurredAt));
		for (var share : expense.shares()) {
			entries.add(new LedgerEntry(this, share.participantId(), share.owedShare().negate(),
					LedgerEntryType.EXPENSE_OWED, expense.expenseId().value(), occurredAt));
		}
	}

	/**
	 * The balance of every person that has entries, in the order they first appear; the
	 * sum is always zero. This is the definition of a balance. It loads every entry, so
	 * the application reads balances with a database query instead (see
	 * {@code GroupLedgerRepository#totals}).
	 */
	public List<MemberBalance> projectBalances() {
		Map<UserId, Money> balances = new LinkedHashMap<>();
		for (var entry : entries) {
			balances.merge(entry.userId(), entry.deltaAmount(), Money::plus);
		}
		return balances.entrySet().stream().map(e -> new MemberBalance(e.getKey(), e.getValue())).toList();
	}

	public GroupId groupId() {
		return new GroupId(groupId);
	}

	public List<LedgerEntry> entries() {
		return List.copyOf(entries);
	}

}
