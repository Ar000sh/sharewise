package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GroupService {

	/** Read snapshot taken inside the transaction, so callers never touch lazy state. */
	record GroupView(UUID id, String name, GroupStatus status, List<MemberView> members) {

		int memberCount() {
			return members.size();
		}

		static GroupView of(Group g) {
			return new GroupView(g.id(), g.name(), g.status(),
					g.members().stream().map(m -> new MemberView(m.memberId(), m.role())).toList());
		}

	}

	record MemberView(UUID memberId, Role role) {
	}

	private final GroupRepository groups;

	private final GroupMembership membership;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	GroupService(GroupRepository groups, GroupMembership membership, ApplicationEventPublisher events, Clock clock) {
		this.groups = groups;
		this.membership = membership;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	GroupView create(String name, UserProfile creator) {
		Group group = groups.save(Group.create(name, creator.userId().value(), clock.instant()));
		GroupId id = new GroupId(group.id());
		events.publishEvent(new GroupCreated(id, creator.userId()));
		events.publishEvent(new MemberJoined(id, creator.userId(), Role.ADMIN));
		return GroupView.of(group);
	}

	@Transactional(readOnly = true)
	List<GroupView> listFor(UserId caller) {
		return groups.findAllByMember(caller.value()).stream().map(GroupView::of).toList();
	}

	@Transactional(readOnly = true)
	GroupView detail(UUID groupId, UserId caller) {
		membership.requireMember(new GroupId(groupId), caller);
		return groups.findById(groupId)
			.map(GroupView::of)
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
	}

}
