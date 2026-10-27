package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.group.GroupService.GroupView;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service: loads the Group aggregate, lets it enforce the rules, publishes
 * the event.
 */
@Service
class InvitationService {

	/** 32 random bytes = 256 bits, above the 128 bit minimum. */
	private static final int TOKEN_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	private final GroupRepository groups;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	InvitationService(GroupRepository groups, ApplicationEventPublisher events, Clock clock) {
		this.groups = groups;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	Invitation create(UUID groupId, UserId caller) {
		Group group = groups.findById(groupId)
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
		return group.invite(caller.value(), newToken(), clock.instant());
	}

	@Transactional
	GroupView accept(String token, UserId caller) {
		UUID groupId = groups.findIdByInvitationToken(token)
			.orElseThrow(() -> new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found"));
		// Lock the group row so two concurrent accepts by the same user cannot both pass
		// the check.
		Group group = groups.findByIdForUpdate(groupId)
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
		group.acceptInvitation(token, caller.value(), clock.instant());
		groups.saveAndFlush(group);
		events.publishEvent(new MemberJoined(new GroupId(group.id()), caller, Role.MEMBER));
		return GroupView.of(group);
	}

	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
