package de.hsh.favs.sharewise.group.domain;

import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.Email;
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
 * An offer to join a group, valid for 7 days (R9). It carries a secret token that makes
 * up the invitation link, and optionally the email address it was meant for. Part of the
 * {@link Group} aggregate, only reachable through it.
 */
@Entity
@Table(name = "invitations")
public class Invitation {

	/** R9: an invitation is valid for 7 days. */
	static final Duration VALIDITY = Duration.ofDays(7);

	@Id
	private UUID id;

	@Column(nullable = false)
	private String token;

	/** Lower case; null for a link invitation. */
	private String email;

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

	static Invitation issue(Email email, String token, UserId createdBy, Instant now) {
		Invitation invitation = new Invitation();
		invitation.id = UUID.randomUUID();
		invitation.token = token;
		invitation.email = email == null ? null : email.value();
		invitation.status = InvitationStatus.PENDING;
		invitation.createdBy = createdBy.value();
		invitation.createdAt = now;
		invitation.expiresAt = now.plus(VALIDITY);
		return invitation;
	}

	/**
	 * Fails unless this invitation can still be used at {@code now}.
	 * @throws DomainException INVITATION_NOT_FOUND when it was already used or declined,
	 * INVITATION_EXPIRED when it is older than 7 days
	 */
	void requireUsable(Instant now) {
		if (status != InvitationStatus.PENDING) {
			throw new DomainException(ErrorCode.INVITATION_NOT_FOUND, "Invitation not found");
		}
		if (isExpiredAt(now)) {
			throw new DomainException(ErrorCode.INVITATION_EXPIRED, "This invitation has expired");
		}
	}

	/**
	 * Marks it used. A link invitation stays PENDING so the same link can admit several
	 * people.
	 */
	void accept() {
		if (email != null) {
			status = InvitationStatus.ACCEPTED;
		}
	}

	boolean isExpiredAt(Instant now) {
		return now.isAfter(expiresAt);
	}

	public InvitationId id() {
		return new InvitationId(id);
	}

	public String token() {
		return token;
	}

	/** The address this invitation was meant for, or null for a link invitation. */
	public Email email() {
		return email == null ? null : new Email(email);
	}

	public InvitationStatus status() {
		return status;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

}
