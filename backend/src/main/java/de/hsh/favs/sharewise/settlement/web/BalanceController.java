package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /groups/{groupId}/balances}: every member's balance, with names, sorted by
 * name.
 */
@RestController
class BalanceController {

	private static final String UNKNOWN_NAME = "Unknown user";

	private final Balances balances;

	private final GroupMembership membership;

	private final UserProfiles profiles;

	private final CurrentUser currentUser;

	BalanceController(Balances balances, GroupMembership membership, UserProfiles profiles, CurrentUser currentUser) {
		this.balances = balances;
		this.membership = membership;
		this.profiles = profiles;
		this.currentUser = currentUser;
	}

	@GetMapping("/groups/{groupId}/balances")
	List<BalanceResponse> getBalances(@PathVariable UUID groupId) {
		var group = new GroupId(groupId);
		membership.requireMember(group, currentUser.get().userId());
		List<MemberBalance> memberBalances = balances.of(group);
		Set<UserId> users = memberBalances.stream().map(MemberBalance::userId).collect(Collectors.toSet());
		Map<UserId, String> names = profiles.findProfiles(users)
			.stream()
			.collect(Collectors.toMap(UserProfile::userId, UserProfile::displayName, (a, b) -> a));
		return memberBalances.stream()
			.map(balance -> new BalanceResponse(balance.userId().value(),
					names.getOrDefault(balance.userId(), UNKNOWN_NAME), balance.netBalance().amount().toPlainString()))
			.sorted(Comparator.comparing(BalanceResponse::displayName, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(BalanceResponse::userId))
			.toList();
	}

}
