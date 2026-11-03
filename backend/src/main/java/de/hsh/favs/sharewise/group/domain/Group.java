package de.hsh.favs.sharewise.group.domain;

import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupMembershipView;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
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
	 * Fails unless {@code user} is an active member. The service calls it before it looks
	 * up the person to invite, so a non-member learns nothing about which accounts exist.
	 * @throws DomainException NOT_A_MEMBER
	 */
	public void requireActiveMember(UserId user) {
		if (!hasActiveMember(user)) {
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group");
		}
	}

	/**
	 * Invites an existing account; valid for 7 days (R9). Any active member may invite.
	 * An earlier invitation of the same person that is still open blocks it, one that
	 * expired or was answered does not.
	 * @return the invitation and the {@link MemberInvited} event
	 * @throws DomainException NOT_A_MEMBER when the inviter is not an active member,
	 * ALREADY_MEMBER when the invitee already is one, ALREADY_INVITED when the invitee
	 * has an open invitation to this group
	 */
	public Invited inviteMember(UserId invitee, UserId inviter, Instant now) {
		requireActiveMember(inviter);
		if (hasActiveMember(invitee)) {
			throw new DomainException(ErrorCode.ALREADY_MEMBER, "This person is already a member of this group");
		}
		if (!pendingInvitationsFor(invitee, now).isEmpty()) {
			throw new DomainException(ErrorCode.ALREADY_INVITED, "This person already has an open invitation");
		}
		Invitation invitation = Invitation.issue(invitee, inviter, now);
		invitations.add(invitation);
		return new Invited(invitation, new MemberInvited(groupId(), invitation.id(), invitee, inviter, now));
	}

	/**
	 * Joins the user as MEMBER through an invitation meant for them (R9). The checks run
	 * in this order: the invitation exists, is for this user and still pending
	 * (INVITATION_NOT_FOUND), is not expired (INVITATION_EXPIRED), and the user is not a
	 * member yet (ALREADY_MEMBER). The group's status is not checked yet because groups
	 * cannot be closed before a later exercise.
	 * @return the {@link MemberJoined} event, carrying who invited the user
	 */
	public MemberJoined acceptInvitation(InvitationId invitationId, UserId user, Instant now) {
		Invitation invitation = invitation(invitationId);
		invitation.requireAnswerableBy(user, now);
		if (hasActiveMember(user)) {
			throw new DomainException(ErrorCode.ALREADY_MEMBER, "You are already a member of this group");
		}
		invitation.accept();
		memberships.add(new Membership(user, GroupRole.MEMBER, now));
		return new MemberJoined(groupId(), user, GroupRole.MEMBER, invitation.createdBy(), now);
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

	public boolean hasActiveMember(UserId user) {
		return memberships.stream().anyMatch(m -> m.isActiveMember() && m.userId().equals(user));
	}

	/**
	 * A snapshot of who is in the group and whether it is open, for other contexts'
	 * rules.
	 */
	public GroupMembershipView membershipView() {
		Set<UserId> active = memberships.stream()
			.filter(Membership::isActiveMember)
			.map(Membership::userId)
			.collect(Collectors.toSet());
		Set<UserId> admins = memberships.stream()
			.filter(m -> m.isActiveMember() && m.role() == GroupRole.ADMIN)
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
		return (int) memberships.stream().filter(Membership::isActiveMember).count();
	}

}
