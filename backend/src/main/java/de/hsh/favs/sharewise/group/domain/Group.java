package de.hsh.favs.sharewise.group.domain;

import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.Email;
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
				new MemberJoined(group.groupId(), creator, GroupRole.ADMIN, now));
	}

	/**
	 * Issues an invitation that is valid for 7 days (R9). Any active member may invite.
	 * @param email the address it is meant for, or null for a link invitation
	 * @param token the secret that makes up the invitation link
	 * @throws DomainException NOT_A_MEMBER when the inviter is not an active member
	 */
	public Invited inviteMember(Email email, UserId inviter, String token, Instant now) {
		if (!hasActiveMember(inviter)) {
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group");
		}
		Invitation invitation = Invitation.issue(email, token, inviter, now);
		invitations.add(invitation);
		return new Invited(invitation, new MemberInvited(groupId(), invitation.id(), invitation.email(), inviter, now));
	}

	/**
	 * The invitation of this group that carries the given link token.
	 * @throws DomainException INVITATION_NOT_FOUND when this group has no such invitation
	 */
	public Invitation invitationWithToken(String token) {
		return invitations.stream()
			.filter(invitation -> invitation.token().equals(token))
			.findFirst()
			.orElseThrow(() -> new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found"));
	}

	/**
	 * Joins the user as MEMBER through one of this group's invitations. The checks run in
	 * this order: the invitation is pending (INVITATION_NOT_FOUND), not expired
	 * (INVITATION_EXPIRED), and the user is not a member yet (ALREADY_MEMBER). The
	 * group's status is not checked yet because groups cannot be closed before a later
	 * exercise.
	 */
	public MemberJoined acceptInvitation(InvitationId invitationId, UserId user, Instant now) {
		Invitation invitation = invitations.stream()
			.filter(candidate -> candidate.id().equals(invitationId))
			.findFirst()
			.orElseThrow(() -> new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found"));
		invitation.requireUsable(now);
		if (hasActiveMember(user)) {
			throw new DomainException(ErrorCode.ALREADY_MEMBER, "You are already a member of this group");
		}
		invitation.accept();
		memberships.add(new Membership(user, GroupRole.MEMBER, now));
		return new MemberJoined(groupId(), user, GroupRole.MEMBER, now);
	}

	public boolean hasActiveMember(UserId user) {
		return memberships.stream().anyMatch(m -> m.isActiveMember() && m.userId().equals(user));
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
