package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.application.GroupService.GroupView;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record GroupDetailResponse(UUID id, String name, GroupStatus status, int memberCount, List<MemberResponse> members) {

	/** One member as the API shows it: who, how they are called, and their role. */
	record MemberResponse(UUID userId, String displayName, GroupRole role) {

	}

	static GroupDetailResponse of(GroupView group, Map<UserId, String> names, String fallbackName) {
		List<MemberResponse> members = group.memberships()
			.stream()
			.map(m -> new MemberResponse(m.userId().value(), names.getOrDefault(m.userId(), fallbackName), m.role()))
			.toList();
		return new GroupDetailResponse(group.id().value(), group.name().value(), group.status(), group.memberCount(),
				members);
	}

}
