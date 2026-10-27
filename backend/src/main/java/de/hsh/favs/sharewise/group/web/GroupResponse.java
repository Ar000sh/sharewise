package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.application.GroupService.GroupView;
import java.util.UUID;

record GroupResponse(UUID id, String name, GroupStatus status, int memberCount) {

	static GroupResponse of(GroupView group) {
		return new GroupResponse(group.id().value(), group.name().value(), group.status(), group.memberCount());
	}

}
