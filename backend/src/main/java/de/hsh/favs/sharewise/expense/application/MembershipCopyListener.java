package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.persistence.MembershipCopyRepository;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the expense context's copy of group membership up to date from the group
 * context's events. Asynchronous, after the group's commit. Applying an event twice, or
 * an older one after a newer one, changes nothing.
 */
@Component
class MembershipCopyListener {

	private final MembershipCopyRepository copy;

	MembershipCopyListener(MembershipCopyRepository copy) {
		this.copy = copy;
	}

	@ApplicationModuleListener
	void on(GroupCreated event) {
		copy.saveGroup(event.groupId(), true, event.occurredAt());
	}

	@ApplicationModuleListener
	void on(MemberJoined event) {
		copy.saveMember(event.groupId(), event.userId(), event.role() == GroupRole.ADMIN, true, event.occurredAt());
	}

}
