package de.hsh.favs.sharewise.group.domain;

import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An offer to join a group, made to one existing account and valid for 7 days (R9). Part
 * of the {@link Group} aggregate, only reachable through it. Only the invited account can
 * answer it.
 *
 * <p>
 * {@code EXPIRED} is never stored: the stored status is PENDING, ACCEPTED or DECLINED,
 * and {@link #statusAt(Instant)} derives EXPIRED from the expiry time. That way no reader
 * can see a stale PENDING.
 */
@Entity
@Table(name = "invitations")
public class Invitation {

	/** R9: an invitation is valid for 7 days. */
	static final Duration VALIDITY = Duration.ofDays(7);

	@Id
	private UUID id;

	@Column(name = "invitee_id", nullable = false)
	private UUID invitee;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvitationStatus status;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected Invitation() {
	}

	static Invitation issue(UserId invitee, UserId createdBy, Instant now) {
		Invitation invitation = new Invitation();
		invitation.id = UUID.randomUUID();
		invitation.invitee = invitee.value();
		invitation.status = InvitationStatus.PENDING;
		invitation.createdBy = createdBy.value();
		invitation.createdAt = now;
		invitation.expiresAt = now.plus(VALIDITY);
		return invitation;
	}

	/**
	 * Where this invitation is at {@code now}: a stored PENDING past its expiry time is
	 * EXPIRED. At the exact expiry instant it is still PENDING.
	 */
	public InvitationStatus statusAt(Instant now) {
		if (status == InvitationStatus.PENDING && now.isAfter(expiresAt)) {
			return InvitationStatus.EXPIRED;
		}
		return status;
	}

	/**
	 * Fails unless {@code user} can still answer this invitation at {@code now}.
	 * @throws DomainException INVITATION_NOT_FOUND when it is meant for somebody else or
	 * was already answered (the cases look the same, so ids cannot be probed),
	 * INVITATION_EXPIRED when it is older than 7 days
	 */
	void requireAnswerableBy(UserId user, Instant now) {
		if (!invitee.equals(user.value()) || status != InvitationStatus.PENDING) {
			throw new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found");
		}
		if (statusAt(now) == InvitationStatus.EXPIRED) {
			throw new DomainException(ErrorCode.INVITATION_EXPIRED, "This invitation has expired");
		}
	}

	void accept() {
		status = InvitationStatus.ACCEPTED;
	}

	void decline() {
		status = InvitationStatus.DECLINED;
	}

	public InvitationId id() {
		return new InvitationId(id);
	}

	/** The account this invitation is for. */
	public UserId invitee() {
		return new UserId(invitee);
	}

	/** The member who sent it. */
	public UserId createdBy() {
		return new UserId(createdBy);
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	/** The stored status; use {@link #statusAt(Instant)} to see expiry. */
	public InvitationStatus status() {
		return status;
	}

}
