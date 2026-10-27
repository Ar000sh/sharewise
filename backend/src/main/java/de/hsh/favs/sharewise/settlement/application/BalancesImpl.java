package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository.MemberTotal;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges stored balances with the current member list (D15); names are looked up, never
 * stored (D14).
 */
@Service
class BalancesImpl implements Balances {

	private static final String UNKNOWN_NAME = "Unknown";

	private final GroupLedgerRepository ledgers;

	private final GroupMembership membership;

	private final UserProfiles users;

	BalancesImpl(GroupLedgerRepository ledgers, GroupMembership membership, UserProfiles users) {
		this.ledgers = ledgers;
		this.membership = membership;
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public List<MemberBalance> of(GroupId group) {
		Map<UserId, Long> stored = ledgers.totals(group.value())
			.stream()
			.collect(Collectors.toMap(total -> new UserId(total.getMemberId()), MemberTotal::getCents));
		return membership.members(group)
			.stream()
			.map(member -> new MemberBalance(member, displayName(member), stored.getOrDefault(member, 0L)))
			.sorted(Comparator.comparing(MemberBalance::displayName, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(balance -> balance.memberId().value()))
			.toList();
	}

	private String displayName(UserId member) {
		return users.find(new UserId(member.value())).map(UserProfile::displayName).orElse(UNKNOWN_NAME);
	}

}
