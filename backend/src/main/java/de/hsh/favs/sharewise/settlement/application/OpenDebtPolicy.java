package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.settlement.api.MemberBalance;
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
	 * The open debt of {@code debtor} to {@code creditor} in EUR, zero when the pair has
	 * no plan edge or the group has no ledger entries.
	 */
	Money openDebt(GroupId group, UserId debtor, UserId creditor) {
		List<MemberBalance> balances = ledgers.balances(group);
		Money edge = SettlementPlan.of(balances)
			.edge(debtor, creditor)
			.minus(reserved(group, debtor.value(), creditor.value()));
		Money debtorDebt = balanceOf(balances, debtor).negate().minus(reserved(group, debtor.value(), null));
		Money creditorCredit = balanceOf(balances, creditor).minus(reserved(group, null, creditor.value()));
		Money open = smallest(edge, smallest(debtorDebt, creditorCredit));
		return open.isNegative() ? Money.eur(BigDecimal.ZERO) : open;
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

	private static Money smallest(Money a, Money b) {
		return a.minus(b).isPositive() ? b : a;
	}

}
