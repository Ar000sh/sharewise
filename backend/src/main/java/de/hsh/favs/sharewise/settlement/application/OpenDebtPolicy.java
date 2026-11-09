package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.domain.DirectedDebt;
import de.hsh.favs.sharewise.settlement.domain.SettlementPlan;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * How much a debtor may still pay (or a creditor waive) in one pair (D3, D5, R17): the
 * smaller of the debt of the debtor and the credit of the creditor, each reduced by what
 * is already reserved, and never below zero. The plan is not asked: balances are pooled,
 * so the plan is only a suggestion of who pays whom, and a payment is a fact the server
 * records up to what the two people owe and are owed. The screen offers less, the plan
 * edge of the pair minus what is reserved on it ({@link #payableEdges}, A3). Callers hold
 * the ledger lock (D6), otherwise two callers can both see the same open debt.
 */
@Component
class OpenDebtPolicy {

	private final GroupLedgerRepository ledgers;

	private final SettlementPaymentRepository payments;

	OpenDebtPolicy(GroupLedgerRepository ledgers, SettlementPaymentRepository payments) {
		this.ledgers = ledgers;
		this.payments = payments;
	}

	/**
	 * One edge of the plan with what is reserved on its pair ({@code pendingAmount}) and
	 * what the screen offers on it ({@code payableAmount}: the edge minus the reserved
	 * part, never more than what {@code record} would accept now, A3).
	 */
	record PayableEdge(DirectedDebt debt, Money pendingAmount, Money payableAmount) {

	}

	/**
	 * The open debt of {@code debtor} to {@code creditor} in EUR, what {@code record} and
	 * {@code waive} accept: zero when the debtor owes nothing, the creditor is owed
	 * nothing, or the group has no ledger entries.
	 */
	Money openDebt(GroupId group, UserId debtor, UserId creditor) {
		return openDebtFromPersonCaps(group, ledgers.balances(group), debtor, creditor);
	}

	/**
	 * Every edge of the group's plan in plan order, each with what the screen offers on
	 * it: the plan edge minus what is reserved on the pair, but never more than the open
	 * debt (F8: one read of the balances for the whole plan).
	 */
	List<PayableEdge> payableEdges(GroupId group) {
		List<MemberBalance> balances = ledgers.balances(group);
		return SettlementPlan.of(balances).simplifiedDebts().stream().map(debt -> {
			Money reservedOnPair = reserved(group, debt.fromDebtor().value(), debt.toCreditor().value());
			Money pairCap = debt.amount().minus(reservedOnPair);
			Money payable = smallerOf(pairCap,
					openDebtFromPersonCaps(group, balances, debt.fromDebtor(), debt.toCreditor()));
			return new PayableEdge(debt, reservedOnPair, payable.isNegative() ? Money.eur(BigDecimal.ZERO) : payable);
		}).toList();
	}

	/**
	 * The smaller of the debtor's and the creditor's remaining amount, never below zero
	 * (R17).
	 */
	private Money openDebtFromPersonCaps(GroupId group, List<MemberBalance> balances, UserId debtor, UserId creditor) {
		Money debtorCap = balanceOf(balances, debtor).negate().minus(reserved(group, debtor.value(), null));
		Money creditorCap = balanceOf(balances, creditor).minus(reserved(group, null, creditor.value()));
		Money smallestCap = smallerOf(debtorCap, creditorCap);
		return smallestCap.isNegative() ? Money.eur(BigDecimal.ZERO) : smallestCap;
	}

	private Money reserved(GroupId group, UUID debtor, UUID creditor) {
		return Money.eur(payments.reserved(group.value(), debtor, creditor));
	}

	private static Money balanceOf(List<MemberBalance> balances, UserId user) {
		return balances.stream()
			.filter(balance -> balance.userId().equals(user))
			.map(MemberBalance::netBalance)
			.findFirst()
			.orElse(Money.eur(BigDecimal.ZERO));
	}

	private static Money smallerOf(Money first, Money second) {
		return first.minus(second).isPositive() ? second : first;
	}

}
