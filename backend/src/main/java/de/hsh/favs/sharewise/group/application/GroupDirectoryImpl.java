package de.hsh.favs.sharewise.group.application;

import de.hsh.favs.sharewise.group.api.GroupDirectory;
import de.hsh.favs.sharewise.group.persistence.GroupRepository;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GroupDirectoryImpl implements GroupDirectory {

	private final GroupRepository groups;

	GroupDirectoryImpl(GroupRepository groups) {
		this.groups = groups;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<GroupId, String> findNames(Set<GroupId> groupIds) {
		Map<GroupId, String> names = new HashMap<>();
		groups.findAllById(groupIds.stream().map(GroupId::value).toList())
			.forEach(group -> names.put(group.groupId(), group.name().value()));
		return Map.copyOf(names);
	}

}
