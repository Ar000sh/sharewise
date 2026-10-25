package de.hsh.favs.sharewise.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitations")
class Invitation {

	/** R9: an invitation is valid for 7 days. */
	static final Duration VALIDITY = Duration.ofDays(7);

	@Id
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

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

	static Invitation issue(UUID groupId, String token, UUID createdBy, Instant now) {
		Invitation i = new Invitation();
		i.id = UUID.randomUUID();
		i.groupId = groupId;
		i.token = token;
		i.createdBy = createdBy;
		i.createdAt = now;
		i.expiresAt = now.plus(VALIDITY);
		return i;
	}

	boolean isExpiredAt(Instant now) {
		return now.isAfter(expiresAt);
	}

	UUID groupId() {
		return groupId;
	}

	String token() {
		return token;
	}

	Instant expiresAt() {
		return expiresAt;
	}

}
