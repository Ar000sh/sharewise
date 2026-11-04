package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.domain.SettlementPlan;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read model behind {@code GET /groups/{groupId}/settlement-plan}: the simplified
 * plan of the booked balances (R15), each edge with the amount already reserved on its
 * pair. Nothing is stored; it is computed per request, so it is as current as the ledger.
 */
@Service
public class SettlementPlanService {

	private final GroupMembership groups;

	private final GroupLedgerRepository ledgers;

	private final SettlementPaymentRepository payments;

	SettlementPlanService(GroupMembership groups, GroupLedgerRepository ledgers, SettlementPaymentRepository payments) {
		this.groups = groups;
		this.ledgers = ledgers;
		this.payments = payments;
	}

	/**
	 * One plan edge: {@code fromDebtor} pays {@code amount} to {@code toCreditor};
	 * {@code pendingAmount} is the part of the pair already reserved by payments that are
	 * PENDING or not yet booked (D5).
	 */
	public record PlanEntry(UserId fromDebtor, UserId toCreditor, Money amount, Money pendingAmount) {

	}

	/**
	 * The plan edges of the group in plan order (by creditor, then debtor id); empty when
	 * everyone is at zero.
	 * @throws de.hsh.favs.sharewise.shared.DomainException with GROUP_NOT_FOUND or
	 * NOT_A_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<PlanEntry> planFor(GroupId group, UserId caller) {
		groups.requireMember(group, caller);
		return SettlementPlan.of(ledgers.balances(group))
			.simplifiedDebts()
			.stream()
			.map(debt -> new PlanEntry(debt.fromDebtor(), debt.toCreditor(), debt.amount(),
					Money.eur(payments.reserved(group.value(), debt.fromDebtor().value(), debt.toCreditor().value()))))
			.toList();
	}

}
