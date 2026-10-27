package de.hsh.favs.sharewise.group.application;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.domain.Group;
import de.hsh.favs.sharewise.group.domain.GroupName;
import de.hsh.favs.sharewise.group.domain.Invitation;
import de.hsh.favs.sharewise.group.domain.Membership;
import de.hsh.favs.sharewise.group.persistence.GroupRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the group aggregate: load it, let it enforce the rules, save it, publish
 * the events it returned.
 */
@Service
public class GroupService {

	/** The currency of every group in Ex4. */
	static final Currency EUR = Currency.getInstance("EUR");

	/** 32 random bytes = 256 bits, above the 128 bit minimum for an unguessable link. */
	private static final int TOKEN_BYTES = 32;

	/**
	 * A read snapshot taken inside the transaction, so callers never touch lazy state.
	 */
	public record GroupView(GroupId id, GroupName name, GroupStatus status, List<MembershipView> memberships) {

		public int memberCount() {
			return memberships.size();
		}

		static GroupView of(Group group) {
			return new GroupView(group.groupId(), group.name(), group.status(),
					group.memberships().stream().filter(Membership::isActiveMember).map(MembershipView::of).toList());
		}

	}

	public record MembershipView(UserId userId, GroupRole role) {

		static MembershipView of(Membership membership) {
			return new MembershipView(membership.userId(), membership.role());
		}

	}

	/** The link token and when it stops working. */
	public record InvitationView(String token, Instant expiresAt) {

		static InvitationView of(Invitation invitation) {
			return new InvitationView(invitation.token(), invitation.expiresAt());
		}

	}

	private final SecureRandom random = new SecureRandom();

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

	/** Creates an OPEN group with the creator as its first ADMIN member. */
	@Transactional
	public GroupView createGroup(GroupName name, UserId creator) {
		Group.Creation creation = Group.createGroup(name, EUR, creator, clock.instant());
		groups.save(creation.group());
		events.publishEvent(creation.groupCreated());
		events.publishEvent(creation.creatorJoined());
		return GroupView.of(creation.group());
	}

	/** The groups the caller is an active member of, oldest first. */
	@Transactional(readOnly = true)
	public List<GroupView> listGroups(UserId caller) {
		return groups.findAllByActiveMember(caller.value()).stream().map(GroupView::of).toList();
	}

	/**
	 * One group with its members.
	 * @throws DomainException GROUP_NOT_FOUND, or NOT_A_MEMBER when the caller is not in
	 * the group
	 */
	@Transactional(readOnly = true)
	public GroupView getGroup(GroupId groupId, UserId caller) {
		membership.requireMember(groupId, caller);
		return groups.findById(groupId.value())
			.map(GroupView::of)
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
	}

	/**
	 * Creates a link invitation to the group.
	 * @throws DomainException GROUP_NOT_FOUND, or NOT_A_MEMBER when the caller is not in
	 * the group
	 */
	@Transactional
	public InvitationView inviteMember(GroupId groupId, UserId inviter) {
		Group group = groups.findById(groupId.value())
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
		Group.Invited invited = group.inviteMember(null, inviter, newToken(), clock.instant());
		events.publishEvent(invited.event());
		return InvitationView.of(invited.invitation());
	}

	/**
	 * Joins the caller to the group the link token belongs to. The group row is locked
	 * while the rules run, so two simultaneous accepts by the same person cannot both
	 * pass the already-a-member check.
	 * @throws DomainException INVITATION_NOT_FOUND, INVITATION_EXPIRED or ALREADY_MEMBER
	 */
	@Transactional
	public GroupView acceptInvitation(String token, UserId caller) {
		UUID groupId = groups.findIdByInvitationToken(token)
			.orElseThrow(() -> new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found"));
		Group group = groups.findByIdForUpdate(groupId)
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
		Invitation invitation = group.invitationWithToken(token);
		var joined = group.acceptInvitation(invitation.id(), caller, clock.instant());
		groups.saveAndFlush(group);
		events.publishEvent(joined);
		return GroupView.of(group);
	}

	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
