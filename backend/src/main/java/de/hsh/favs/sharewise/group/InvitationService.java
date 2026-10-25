package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.group.GroupService.GroupView;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.UserId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InvitationService {

	/** 32 random bytes = 256 bits, above the 128 bit minimum. */
	private static final int TOKEN_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	private final InvitationRepository invitations;

	private final GroupRepository groups;

	private final GroupMembership membership;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	InvitationService(InvitationRepository invitations, GroupRepository groups, GroupMembership membership,
			ApplicationEventPublisher events, Clock clock) {
		this.invitations = invitations;
		this.groups = groups;
		this.membership = membership;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	Invitation create(UUID groupId, UserId caller) {
		membership.requireMember(new GroupId(groupId), new MemberId(caller.value()));
		return invitations.save(Invitation.issue(groupId, newToken(), caller.value(), clock.instant()));
	}

	@Transactional
	GroupView accept(String token, UserId caller) {
		Invitation invitation = invitations.findByToken(token)
				.orElseThrow(() -> new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found"));
		Instant now = clock.instant();
		if (invitation.isExpiredAt(now)) {
			throw new DomainException(ErrorCode.INVITATION_EXPIRED, "This invitation has expired");
		}
		// Lock the group row so two concurrent accepts by the same user cannot both pass the check.
		Group group = groups.findByIdForUpdate(invitation.groupId())
				.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
		if (group.hasMember(caller.value())) {
			throw new DomainException(ErrorCode.ALREADY_MEMBER, "You are already a member of this group");
		}
		group.addMember(caller.value(), now);
		groups.saveAndFlush(group);
		events.publishEvent(
				new MemberJoined(new GroupId(group.id()), new MemberId(caller.value()), Role.MEMBER));
		return GroupView.of(group);
	}

	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
