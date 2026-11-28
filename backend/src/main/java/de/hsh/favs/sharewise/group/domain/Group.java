package de.hsh.favs.sharewise.group.domain;

import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupMembershipView;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.MemberLeft;
import de.hsh.favs.sharewise.group.api.MemberPromoted;
import de.hsh.favs.sharewise.group.api.MemberRemoved;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A group of people who share expenses. Aggregate root of the group context: it owns its
 * {@link Membership}s and {@link Invitation}s, and every rule about who may join goes
 * through its operations. Each operation returns the domain event it produced; the caller
 * publishes it.
 */
@Entity
@Table(name = "groups")
public class Group {

	/** The group together with the events of its creation. */
	public record Creation(Group group, GroupCreated groupCreated, MemberJoined creatorJoined) {

	}

	/** The invitation together with the event that announces it. */
	public record Invited(Invitation invitation, MemberInvited event) {

	}

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GroupStatus status;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id", nullable = false)
	@OrderBy("joinedAt ASC, userId ASC")
	private List<Membership> memberships = new ArrayList<>();

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id", nullable = false)
	private List<Invitation> invitations = new ArrayList<>();

	protected Group() {
	}

	/** Creates an OPEN group whose creator is its first member, with the ADMIN role. */
	public static Creation createGroup(GroupName name, Currency currency, UserId creator, Instant now) {
		Group group = new Group();
		group.id = UUID.randomUUID();
		group.name = name.value();
		group.status = GroupStatus.OPEN;
		group.currency = currency.getCurrencyCode();
		group.createdAt = now;
		group.memberships.add(new Membership(creator, GroupRole.ADMIN, now));
		return new Creation(group, new GroupCreated(group.groupId(), creator, now),
				new MemberJoined(group.groupId(), creator, GroupRole.ADMIN, null, now));
	}

	/**
	 * Fails unless {@code user} is an active admin. The service calls it before it looks
	 * up the person to invite, so a non-member learns nothing about which accounts exist.
	 * @throws DomainException NOT_A_MEMBER when the user is not in the group,
	 * NOT_AN_ADMIN when they are in it as a plain member
	 */
	public void requireAdmin(UserId user) {
		Membership membership = activeMemberships().stream()
			.filter(m -> m.userId().equals(user))
			.findFirst()
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group"));
		if (membership.role() != GroupRole.ADMIN) {
			throw new DomainException(ErrorCode.NOT_AN_ADMIN, "Only an admin can do this");
		}
	}

	/**
	 * Invites an existing account to join with {@code role}; valid for 7 days (R9). Only
	 * an admin may invite. An earlier invitation of the same person that is still open
	 * blocks it, one that expired or was answered does not.
	 * <p>
	 * The inviter is <em>not</em> checked here: the service calls {@link #requireAdmin}
	 * first, as its own step, before it looks up the invitee.
	 * @return the invitation and the {@link MemberInvited} event
	 * @throws DomainException ALREADY_MEMBER when the invitee already is a member,
	 * ALREADY_INVITED when the invitee has an open invitation to this group
	 */
	public Invited inviteMember(UserId invitee, UserId inviter, GroupRole role, Instant now) {
		if (hasActiveMember(invitee)) {
			throw new DomainException(ErrorCode.ALREADY_MEMBER, "This person is already a member of this group");
		}
		if (!pendingInvitationsFor(invitee, now).isEmpty()) {
			throw new DomainException(ErrorCode.ALREADY_INVITED, "This person already has an open invitation");
		}
		Invitation invitation = Invitation.issue(invitee, role, inviter, now);
		invitations.add(invitation);
		return new Invited(invitation, new MemberInvited(groupId(), invitation.id(), invitee, inviter, role, now));
	}

	/**
	 * Makes an active member an admin; one way, nobody is demoted. Only an admin may
	 * promote (J10). The checks run in this order: the promoter is an admin
	 * (NOT_A_MEMBER, NOT_AN_ADMIN), the user is an active member (MEMBER_NOT_FOUND), the
	 * user is not an admin yet (ALREADY_ADMIN).
	 * @return the {@link MemberPromoted} event
	 */
	public MemberPromoted promoteToAdmin(UserId user, UserId promotedBy, Instant now) {
		requireAdmin(promotedBy);
		Membership membership = activeMemberships().stream()
			.filter(m -> m.userId().equals(user))
			.findFirst()
			.orElseThrow(
					() -> new DomainException(ErrorCode.MEMBER_NOT_FOUND, "This person is not a member of this group"));
		if (membership.role() == GroupRole.ADMIN) {
			throw new DomainException(ErrorCode.ALREADY_ADMIN, "This member is already an admin");
		}
		membership.promoteToAdmin();
		return new MemberPromoted(groupId(), user, promotedBy, now);
	}

	/**
	 * Joins the user with the role the invitation carries (R9). The checks run in this
	 * order: the invitation exists, is for this user and still pending
	 * (INVITATION_NOT_FOUND), is not expired (INVITATION_EXPIRED), and the user is not a
	 * member yet (ALREADY_MEMBER). The group's status is not checked yet because groups
	 * cannot be closed before a later exercise. A former member gets their old membership
	 * back with the invitation's role (design 2026-11-28 M3).
	 * @return the {@link MemberJoined} event, carrying who invited the user
	 */
	public MemberJoined acceptInvitation(InvitationId invitationId, UserId user, Instant now) {
		Invitation invitation = invitation(invitationId);
		invitation.requireAnswerableBy(user, now);
		if (hasActiveMember(user)) {
			throw new DomainException(ErrorCode.ALREADY_MEMBER, "You are already a member of this group");
		}
		invitation.accept();
		memberships.stream()
			.filter(m -> m.userId().equals(user))
			.findFirst()
			.ifPresentOrElse(former -> former.rejoin(invitation.role(), now),
					() -> memberships.add(new Membership(user, invitation.role(), now)));
		return new MemberJoined(groupId(), user, invitation.role(), invitation.createdBy(), now);
	}

	/**
	 * The member leaves the group and becomes a former member, still shown in old
	 * expenses (J10, R13). Only at balance 0 (R11), as the ledger last reported it; the
	 * last admin cannot leave while other members remain (R12). The checks run in this
	 * order: the user is an active member (NOT_A_MEMBER), their balance is zero
	 * (BALANCE_NOT_ZERO), they are not the last admin of a group with other members
	 * (LAST_ADMIN).
	 * @return the {@link MemberLeft} event
	 * @throws DomainException NOT_A_MEMBER, BALANCE_NOT_ZERO, LAST_ADMIN
	 */
	public MemberLeft leaveGroup(UserId member, Instant now) {
		Membership membership = activeMemberships().stream()
			.filter(m -> m.userId().equals(member))
			.findFirst()
			.orElseThrow(() -> new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group"));
		if (!membership.hasZeroBalance()) {
			throw new DomainException(ErrorCode.BALANCE_NOT_ZERO, "Settle your balance before leaving the group");
		}
		boolean othersRemain = activeMemberships().size() > 1;
		long admins = activeMemberships().stream().filter(m -> m.role() == GroupRole.ADMIN).count();
		if (membership.role() == GroupRole.ADMIN && admins == 1 && othersRemain) {
			throw new DomainException(ErrorCode.LAST_ADMIN, "Make another member an admin before you leave");
		}
		membership.leave();
		return new MemberLeft(groupId(), member, now);
	}

	/**
	 * An admin removes another member, who becomes a former member, still shown in old
	 * expenses (J11, R13). Only at that member's balance 0 (R11). An admin cannot remove
	 * themselves; leaving goes through {@link #leaveGroup}, where R12 applies (design
	 * 2026-11-28 M5). The checks run in this order: the caller is an admin (NOT_A_MEMBER,
	 * NOT_AN_ADMIN), the target is not the caller (CANNOT_REMOVE_YOURSELF), the target is
	 * an active member (MEMBER_NOT_FOUND), the target's balance is zero
	 * (BALANCE_NOT_ZERO).
	 * @return the {@link MemberRemoved} event
	 * @throws DomainException NOT_A_MEMBER, NOT_AN_ADMIN, CANNOT_REMOVE_YOURSELF,
	 * MEMBER_NOT_FOUND, BALANCE_NOT_ZERO
	 */
	public MemberRemoved removeMember(UserId targetMember, UserId actingAdmin, Instant now) {
		requireAdmin(actingAdmin);
		if (targetMember.equals(actingAdmin)) {
			throw new DomainException(ErrorCode.CANNOT_REMOVE_YOURSELF, "Use Leave group to leave it yourself");
		}
		Membership membership = activeMemberships().stream()
			.filter(m -> m.userId().equals(targetMember))
			.findFirst()
			.orElseThrow(
					() -> new DomainException(ErrorCode.MEMBER_NOT_FOUND, "This person is not a member of this group"));
		if (!membership.hasZeroBalance()) {
			throw new DomainException(ErrorCode.BALANCE_NOT_ZERO, "This member's balance must be settled first");
		}
		membership.leave();
		return new MemberRemoved(groupId(), targetMember, actingAdmin, now);
	}

	/**
	 * Turns an invitation down; nobody joins. The same checks as accepting, except that
	 * membership is irrelevant.
	 * @return the {@link InvitationDeclined} event, carrying who invited the user
	 * @throws DomainException INVITATION_NOT_FOUND, INVITATION_EXPIRED
	 */
	public InvitationDeclined declineInvitation(InvitationId invitationId, UserId user, Instant now) {
		Invitation invitation = invitation(invitationId);
		invitation.requireAnswerableBy(user, now);
		invitation.decline();
		return new InvitationDeclined(groupId(), invitation.id(), user, invitation.createdBy(), now);
	}

	/**
	 * Flips every stored PENDING invitation whose 7-day validity has passed to EXPIRED
	 * (R9). Idempotent; returns how many invitations changed status. Produces no event
	 * (the diagram draws none for expiry).
	 */
	public int expirePendingInvitations(Instant now) {
		int expired = 0;
		for (Invitation invitation : invitations) {
			if (invitation.expire(now)) {
				expired++;
			}
		}
		return expired;
	}

	/**
	 * The invitations of this group that are open for {@code user} at {@code now}: stored
	 * PENDING and not expired.
	 */
	public List<Invitation> pendingInvitationsFor(UserId user, Instant now) {
		return invitations.stream()
			.filter(i -> i.invitee().equals(user) && i.statusAt(now) == InvitationStatus.PENDING)
			.toList();
	}

	private Invitation invitation(InvitationId invitationId) {
		return invitations.stream()
			.filter(candidate -> candidate.id().equals(invitationId))
			.findFirst()
			.orElseThrow(() -> new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found"));
	}

	/**
	 * Records whether a member's balance in this group is zero, as the settlement ledger
	 * reports it (R11: a member may only leave at balance 0; D12). Idempotent; a status
	 * older than the one stored is ignored, because the ledger's events may arrive out of
	 * order. A user who is not an active member is ignored without error (the event may
	 * arrive after they left). Returns no event: the diagram draws none for it.
	 * @param occurredAt when the ledger booking that changed the balance happened
	 */
	public void updateBalanceStatus(UserId member, boolean hasZeroBalance, Instant occurredAt) {
		memberships.stream()
			.filter(m -> m.isActiveMember() && m.userId().equals(member))
			.findFirst()
			.ifPresent(m -> m.updateBalanceStatus(hasZeroBalance, occurredAt));
	}

	public boolean hasActiveMember(UserId user) {
		return activeMemberships().stream().anyMatch(m -> m.userId().equals(user));
	}

	/** The memberships of people who are currently in the group. */
	public List<Membership> activeMemberships() {
		return memberships.stream().filter(Membership::isActiveMember).toList();
	}

	/** The memberships of people who left or were removed (R13). */
	public List<Membership> formerMemberships() {
		return memberships.stream().filter(m -> !m.isActiveMember()).toList();
	}

	/**
	 * A snapshot of who is in the group and whether it is open, for other contexts'
	 * rules.
	 */
	public GroupMembershipView membershipView() {
		Set<UserId> active = activeMemberships().stream().map(Membership::userId).collect(Collectors.toSet());
		Set<UserId> admins = activeMemberships().stream()
			.filter(m -> m.role() == GroupRole.ADMIN)
			.map(Membership::userId)
			.collect(Collectors.toSet());
		return new GroupMembershipView(groupId(), status == GroupStatus.OPEN, active, admins);
	}

	public GroupId groupId() {
		return new GroupId(id);
	}

	public GroupName name() {
		return new GroupName(name);
	}

	public GroupStatus status() {
		return status;
	}

	public Currency currency() {
		return Currency.getInstance(currency);
	}

	public List<Membership> memberships() {
		return List.copyOf(memberships);
	}

	public int memberCount() {
		return activeMemberships().size();
	}

}
