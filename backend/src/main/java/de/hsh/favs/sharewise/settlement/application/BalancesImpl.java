package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository.UserTotal;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges the stored balances with the group's current member list (D15), so a member
 * without expenses is listed with zero. All amounts are EUR, the only currency a group
 * can have in Ex4.
 */
@Service
class BalancesImpl implements Balances {

	private final GroupLedgerRepository ledgers;

	private final GroupMembership membership;

	BalancesImpl(GroupLedgerRepository ledgers, GroupMembership membership) {
		this.ledgers = ledgers;
		this.membership = membership;
	}

	@Override
	@Transactional(readOnly = true)
	public List<MemberBalance> of(GroupId group) {
		Map<UserId, BigDecimal> stored = ledgers.totals(group.value())
			.stream()
			.collect(Collectors.toMap(total -> new UserId(total.getUserId()), UserTotal::getAmount));
		return membership.members(group)
			.stream()
			.map(user -> new MemberBalance(user, Money.eur(stored.getOrDefault(user, BigDecimal.ZERO))))
			.toList();
	}

}
