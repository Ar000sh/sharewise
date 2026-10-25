package de.hsh.favs.sharewise.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Part of the Group aggregate: only reachable and changed through {@link Group}. */
@Entity
@Table(name = "invitations")
class Invitation {

	/** R9: an invitation is valid for 7 days. */
	static final Duration VALIDITY = Duration.ofDays(7);

	@Id
	private UUID id;

	@Column(nullable = false)
	private String token;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected Invitation() {
	}

	static Invitation issue(String token, UUID createdBy, Instant now) {
		Invitation i = new Invitation();
		i.id = UUID.randomUUID();
		i.token = token;
		i.createdBy = createdBy;
		i.createdAt = now;
		i.expiresAt = now.plus(VALIDITY);
		return i;
	}

	boolean isExpiredAt(Instant now) {
		return now.isAfter(expiresAt);
	}

	String token() {
		return token;
	}

	Instant expiresAt() {
		return expiresAt;
	}

}
