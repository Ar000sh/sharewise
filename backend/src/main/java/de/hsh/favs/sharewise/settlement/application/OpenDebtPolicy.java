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
 * smallest of the plan edge of the pair, the debt of the debtor and the credit of the
 * creditor, each reduced by what is already reserved, and never below zero. The pair cap
 * matches the plan the user sees; the person caps make sure no balance crosses zero even
 * when the plan changes later. Callers hold the ledger lock (D6), otherwise two callers
 * can both see the same open debt.
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
	 * the open debt of the pair ({@code payableAmount}, what {@code record} would accept
	 * now, A3).
	 */
	record PayableEdge(DirectedDebt debt, Money pendingAmount, Money payableAmount) {

	}

	/**
	 * The open debt of {@code debtor} to {@code creditor} in EUR, zero when the pair has
	 * no plan edge or the group has no ledger entries.
	 */
	Money openDebt(GroupId group, UserId debtor, UserId creditor) {
		List<MemberBalance> balances = ledgers.balances(group);
		Money plannedAmount = SettlementPlan.of(balances).plannedAmount(debtor, creditor);
		return openDebtFromCaps(group, balances, debtor, creditor, plannedAmount,
				reserved(group, debtor.value(), creditor.value()));
	}

	/**
	 * Every edge of the group's plan in plan order, each with its open debt computed by
	 * the same rule as {@link #openDebt}, from one read of the balances (F8).
	 */
	List<PayableEdge> payableEdges(GroupId group) {
		List<MemberBalance> balances = ledgers.balances(group);
		return SettlementPlan.of(balances).simplifiedDebts().stream().map(debt -> {
			Money reservedOnPair = reserved(group, debt.fromDebtor().value(), debt.toCreditor().value());
			return new PayableEdge(debt, reservedOnPair, openDebtFromCaps(group, balances, debt.fromDebtor(),
					debt.toCreditor(), debt.amount(), reservedOnPair));
		}).toList();
	}

	/** The one formula: the smallest of the three caps, never below zero. */
	private Money openDebtFromCaps(GroupId group, List<MemberBalance> balances, UserId debtor, UserId creditor,
			Money plannedAmount, Money reservedOnPair) {
		Money pairCap = plannedAmount.minus(reservedOnPair);
		Money debtorCap = balanceOf(balances, debtor).negate().minus(reserved(group, debtor.value(), null));
		Money creditorCap = balanceOf(balances, creditor).minus(reserved(group, null, creditor.value()));
		Money smallestCap = smallerOf(pairCap, smallerOf(debtorCap, creditorCap));
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
