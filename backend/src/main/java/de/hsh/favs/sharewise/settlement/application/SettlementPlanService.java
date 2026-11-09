package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read model behind {@code GET /groups/{groupId}/settlement-plan}: the simplified
 * plan of the booked balances (R15), each edge with the amount already reserved on its
 * pair and the amount the server would accept on {@code record} now (A3). Nothing is
 * stored; it is computed per request, so it is as current as the ledger.
 */
@Service
public class SettlementPlanService {

	private final GroupMembership groups;

	private final OpenDebtPolicy openDebtPolicy;

	SettlementPlanService(GroupMembership groups, OpenDebtPolicy openDebtPolicy) {
		this.groups = groups;
		this.openDebtPolicy = openDebtPolicy;
	}

	/**
	 * One plan edge: {@code fromDebtor} pays {@code amount} to {@code toCreditor};
	 * {@code pendingAmount} is the part of the pair already reserved by payments that are
	 * PENDING or not yet booked (D5); {@code payableAmount} is the open debt of the pair,
	 * computed by {@link OpenDebtPolicy} exactly as {@code record} computes it, so the
	 * screen and the server cannot disagree (A3, R17). It can be below
	 * {@code amount - pendingAmount} when the debtor's debt is reserved towards another
	 * creditor.
	 */
	public record PlanEntry(UserId fromDebtor, UserId toCreditor, Money amount, Money pendingAmount,
			Money payableAmount) {

	}

	/**
	 * The plan edges of the group in plan order (by creditor, then debtor id); empty when
	 * everyone is at zero.
	 * @throws de.hsh.favs.sharewise.shared.DomainException with GROUP_NOT_FOUND or
	 * NOT_A_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<PlanEntry> listPlanEntries(GroupId group, UserId caller) {
		groups.requireMember(group, caller);
		return openDebtPolicy.payableEdges(group)
			.stream()
			.map(edge -> new PlanEntry(edge.debt().fromDebtor(), edge.debt().toCreditor(), edge.debt().amount(),
					edge.pendingAmount(), edge.payableAmount()))
			.toList();
	}

}
