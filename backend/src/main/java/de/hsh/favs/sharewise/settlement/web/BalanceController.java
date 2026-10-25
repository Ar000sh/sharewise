package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Driving adapter for {@code GET /groups/{groupId}/balances}. */
@RestController
class BalanceController {

	private final Balances balances;
	private final GroupMembership membership;
	private final CurrentUser currentUser;

	BalanceController(Balances balances, GroupMembership membership, CurrentUser currentUser) {
		this.balances = balances;
		this.membership = membership;
		this.currentUser = currentUser;
	}

	record BalanceResponse(UUID memberId, String displayName, long balanceCents) {
	}

	@GetMapping("/groups/{groupId}/balances")
	List<BalanceResponse> balances(@PathVariable UUID groupId) {
		var group = new GroupId(groupId);
		membership.requireMember(group, new MemberId(currentUser.get().id().value()));
		return balances.of(group).stream()
				.map(b -> new BalanceResponse(b.memberId().value(), b.displayName(), b.balanceCents()))
				.toList();
	}

}
