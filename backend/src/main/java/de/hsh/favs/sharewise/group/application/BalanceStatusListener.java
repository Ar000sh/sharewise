package de.hsh.favs.sharewise.group.application;

import de.hsh.favs.sharewise.shared.events.MemberBalanceStatusChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the ledger's balance status into a call on the group service (R11, D12).
 * Asynchronous, after the commit.
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

}
