package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root: the running balance of every member of one group. It owns the rule (R14) and the guard
 * against counting an expense twice.
 */
@Entity
@Table(name = "settlement_ledger")
public class GroupLedger {

	@Id
	@Column(name = "group_id")
	private UUID groupId;

	@ElementCollection
	@CollectionTable(name = "settlement_balance", joinColumns = @JoinColumn(name = "group_id"))
	@MapKeyColumn(name = "member_id")
	@Column(name = "balance_cents", nullable = false)
	private Map<UUID, Long> balances = new HashMap<>();

	@ElementCollection
	@CollectionTable(name = "settlement_processed_expense", joinColumns = @JoinColumn(name = "group_id"))
	@Column(name = "expense_id", nullable = false)
	private Set<UUID> processedExpenses = new HashSet<>();

	protected GroupLedger() {
	}

	public GroupLedger(GroupId group) {
		this.groupId = group.value();
	}

	/** R14: the payer is credited the total, every share holder is debited their share. Returns false for a repeat. */
	public boolean apply(ExpenseRecorded expense) {
		if (!processedExpenses.add(expense.expenseId())) {
			return false;
		}
		balances.merge(expense.payerId().value(), expense.totalCents(), Long::sum);
		for (var share : expense.shares()) {
			balances.merge(share.memberId().value(), -share.shareCents(), Long::sum);
		}
		return true;
	}

	/** Only members that appear in an expense; the sum is always 0. */
	public Map<MemberId, Long> balances() {
		Map<MemberId, Long> snapshot = new HashMap<>();
		balances.forEach((member, cents) -> snapshot.put(new MemberId(member), cents));
		return snapshot;
	}

}
