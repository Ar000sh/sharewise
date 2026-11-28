package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseCorrected;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseRemoved;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
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
 * every participant debited their share, R14), and so does every confirmed settlement
 * payment or waived debt (R16, R17); a person's balance is the sum of their entries, and
 * the balances of a group always add up to zero. Booking an expense twice is prevented by
 * the service and, as a last line of defence, by a unique constraint.
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
	 * Books a correction as the difference between the new and the old version: one
	 * {@code EXPENSE_PAID} and one {@code EXPENSE_OWED} entry per person whose amount
	 * changed, all referencing the correction (R7, R14, design
	 * 2026-11-28-expense-correction E5). The booking adds up to zero, and the result is
	 * the same in whatever order the events arrive, because it only adds.
	 */
	public void appendCorrectionReversal(ExpenseCorrected correction, Instant occurredAt) {
		Map<UserId, Money> paid = new LinkedHashMap<>();
		paid.merge(correction.previousPaidBy(), correction.previousTotalAmount().negate(), Money::plus);
		paid.merge(correction.paidBy(), correction.totalAmount(), Money::plus);
		Map<UserId, Money> owed = new LinkedHashMap<>();
		correction.previousShares().forEach(share -> owed.merge(share.participantId(), share.owedShare(), Money::plus));
		correction.shares()
			.forEach(share -> owed.merge(share.participantId(), share.owedShare().negate(), Money::plus));
		appendChanges(paid, LedgerEntryType.EXPENSE_PAID, correction.revisionId(), occurredAt);
		appendChanges(owed, LedgerEntryType.EXPENSE_OWED, correction.revisionId(), occurredAt);
	}

	/**
	 * Takes a removed expense back: the payer is debited what they paid, every
	 * participant credited their share, all referencing the removal (R8, R14, design
	 * 2026-11-28-expense-correction E5).
	 */
	public void appendRemovalReversal(ExpenseRemoved removal, Instant occurredAt) {
		entries.add(new LedgerEntry(this, removal.paidBy(), removal.totalAmount().negate(),
				LedgerEntryType.EXPENSE_PAID, removal.removalId(), occurredAt));
		for (var share : removal.shares()) {
			entries.add(new LedgerEntry(this, share.participantId(), share.owedShare(), LedgerEntryType.EXPENSE_OWED,
					removal.removalId(), occurredAt));
		}
	}

	private void appendChanges(Map<UserId, Money> changes, LedgerEntryType type, UUID referenceId, Instant occurredAt) {
		changes.forEach((user, delta) -> {
			if (!delta.isZero()) {
				entries.add(new LedgerEntry(this, user, delta, type, referenceId, occurredAt));
			}
		});
	}

	/**
	 * Books a confirmed settlement payment: the debtor's balance rises by the amount
	 * ({@code PAYMENT_SENT}), the creditor's falls by it ({@code PAYMENT_RECEIVED}), so
	 * the sum stays zero. Both entries reference the payment id (R14, R16, design D8).
	 */
	public void appendSettlementPayment(SettlementPaymentConfirmed event, Instant now) {
		entries.add(new LedgerEntry(this, event.fromDebtor(), event.amount(), LedgerEntryType.PAYMENT_SENT,
				event.paymentId().value(), now));
		entries.add(new LedgerEntry(this, event.toCreditor(), event.amount().negate(), LedgerEntryType.PAYMENT_RECEIVED,
				event.paymentId().value(), now));
	}

	/**
	 * Books a waived debt: the debtor's balance rises by the amount, the creditor's falls
	 * by it, both as {@code DEBT_WAIVED} referencing the payment id (R14, R17, design
	 * D8).
	 */
	public void appendDebtWaiver(DebtWaived event, Instant now) {
		entries.add(new LedgerEntry(this, event.fromDebtor(), event.amount(), LedgerEntryType.DEBT_WAIVED,
				event.paymentId().value(), now));
		entries.add(new LedgerEntry(this, event.toCreditor(), event.amount().negate(), LedgerEntryType.DEBT_WAIVED,
				event.paymentId().value(), now));
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

	/**
	 * The fewest payments that bring every balance to zero (R15), computed from
	 * {@link #projectBalances()}. The ledger itself does not change.
	 */
	public SettlementPlan projectSimplifiedPlan() {
		return SettlementPlan.of(projectBalances());
	}

	public GroupId groupId() {
		return new GroupId(groupId);
	}

	public List<LedgerEntry> entries() {
		return List.copyOf(entries);
	}

}
