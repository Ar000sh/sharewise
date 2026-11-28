package de.hsh.favs.sharewise.group.application;

import de.hsh.favs.sharewise.shared.events.AllBalancesSettled;
import de.hsh.favs.sharewise.shared.events.MemberBalanceStatusChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the ledger's balance events into calls on the group service: one member's balance
 * flag (R11, D12), and the automatic archive of a closed group once nobody owes anything
 * (L5). Asynchronous, after the commit.
 */
@Component
class BalanceStatusListener {

	private final GroupService groups;

	BalanceStatusListener(GroupService groups) {
		this.groups = groups;
	}

	@ApplicationModuleListener
	void on(MemberBalanceStatusChanged event) {
		groups.updateBalanceStatus(event.groupId(), event.userId(), event.hasZeroBalance(), event.occurredAt());
	}

	@ApplicationModuleListener
	void on(AllBalancesSettled event) {
		groups.archiveWhenAllBalancesSettled(event.groupId());
	}

}
