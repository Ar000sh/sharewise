package de.hsh.favs.sharewise.group.application;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.domain.Group;
import de.hsh.favs.sharewise.group.domain.GroupName;
import de.hsh.favs.sharewise.group.domain.Invitation;
import de.hsh.favs.sharewise.group.domain.Membership;
import de.hsh.favs.sharewise.group.persistence.GroupRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.time.Instant;
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

	/**
	 * A read snapshot taken inside the transaction, so callers never touch lazy state.
	 */
	public record GroupView(GroupId id, GroupName name, GroupStatus status, List<MembershipView> memberships) {

		public int memberCount() {
			return memberships.size();
		}

		static GroupView of(Group group) {
			return new GroupView(group.groupId(), group.name(), group.status(),
					group.activeMemberships().stream().map(MembershipView::of).toList());
		}

	}

	public record MembershipView(UserId userId, GroupRole role) {

		static MembershipView of(Membership membership) {
			return new MembershipView(membership.userId(), membership.role());
		}

	}

	/** A new invitation: who it is for and when it stops working. */
	public record InvitationView(InvitationId id, UserProfile invitee, Instant expiresAt) {

		static InvitationView of(Invitation invitation, UserProfile invitee) {
			return new InvitationView(invitation.id(), invitee, invitation.expiresAt());
		}

	}

	/** An open invitation of the caller, with the name of the group it is for. */
	public record PendingInvitationView(InvitationId id, GroupId groupId, String groupName, UserId invitedBy,
			GroupRole role, Instant expiresAt) {

	}

	private final GroupRepository groups;

	private final GroupMembership membership;

	private final UserProfiles profiles;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	GroupService(GroupRepository groups, GroupMembership membership, UserProfiles profiles,
			ApplicationEventPublisher events, Clock clock) {
		this.groups = groups;
		this.membership = membership;
		this.profiles = profiles;
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
	 * Invites the account that {@code identifier} (exact username or email) names, to
	 * join with {@code role}. The group row is locked, so two admins inviting the same
	 * person together are serialized and the second gets ALREADY_INVITED. The inviter's
	 * role is checked before the account is looked up, so a non-member learns nothing
	 * about which accounts exist.
	 * @throws DomainException GROUP_NOT_FOUND, NOT_A_MEMBER, NOT_AN_ADMIN,
	 * ACCOUNT_NOT_FOUND, ALREADY_MEMBER or ALREADY_INVITED
	 */
	@Transactional
	public InvitationView inviteMember(GroupId groupId, UserId inviter, String identifier, GroupRole role) {
		Group group = lockedGroup(groupId.value());
		group.requireAdmin(inviter);
		UserProfile invitee = profiles.findByIdentifier(identifier)
			.orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "No account found"));
		Group.Invited invited = group.inviteMember(invitee.userId(), inviter, role, clock.instant());
		events.publishEvent(invited.event());
		return InvitationView.of(invited.invitation(), invitee);
	}

	/**
	 * Makes {@code user} an admin. The group row is locked, so two admins promoting the
	 * same person together are serialized and the second gets ALREADY_ADMIN.
	 * @throws DomainException GROUP_NOT_FOUND, NOT_A_MEMBER, NOT_AN_ADMIN,
	 * MEMBER_NOT_FOUND or ALREADY_ADMIN
	 */
	@Transactional
	public MembershipView promoteToAdmin(GroupId groupId, UserId caller, UserId user) {
		Group group = lockedGroup(groupId.value());
		var promoted = group.promoteToAdmin(user, caller, clock.instant());
		events.publishEvent(promoted);
		return new MembershipView(user, GroupRole.ADMIN);
	}

	/**
	 * Joins the caller to the group the invitation belongs to. The group row is locked
	 * while the rules run, so two simultaneous answers to the same invitation cannot both
	 * pass.
	 * @throws DomainException INVITATION_NOT_FOUND (also for somebody else's invitation),
	 * INVITATION_EXPIRED or ALREADY_MEMBER
	 */
	@Transactional
	public GroupView acceptInvitation(InvitationId invitationId, UserId caller) {
		Group group = lockedGroupOfInvitation(invitationId);
		var joined = group.acceptInvitation(invitationId, caller, clock.instant());
		groups.saveAndFlush(group);
		events.publishEvent(joined);
		return GroupView.of(group);
	}

	/**
	 * Turns the caller's invitation down.
	 * @throws DomainException INVITATION_NOT_FOUND (also for somebody else's invitation),
	 * INVITATION_EXPIRED
	 */
	@Transactional
	public void declineInvitation(InvitationId invitationId, UserId caller) {
		Group group = lockedGroupOfInvitation(invitationId);
		var declined = group.declineInvitation(invitationId, caller, clock.instant());
		groups.saveAndFlush(group);
		events.publishEvent(declined);
	}

	/**
	 * The caller's open invitations, newest first. A read use case: one query that
	 * returns plain rows, so no group is loaded and no lazy collection is touched.
	 */
	@Transactional(readOnly = true)
	public List<PendingInvitationView> listPendingInvitations(UserId caller) {
		return groups.findPendingInvitationsFor(caller.value(), clock.instant())
			.stream()
			.map(row -> new PendingInvitationView(new InvitationId(row.invitationId()), new GroupId(row.groupId()),
					row.groupName(), new UserId(row.invitedBy()), row.role(), row.expiresAt()))
			.toList();
	}

	private Group lockedGroup(UUID groupId) {
		return groups.findByIdForUpdate(groupId)
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
	}

	private Group lockedGroupOfInvitation(InvitationId invitationId) {
		UUID groupId = groups.findIdByInvitationId(invitationId.value())
			.orElseThrow(() -> new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found"));
		return lockedGroup(groupId);
	}

}
