package de.hsh.favs.sharewise.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A signed-in browser session. Only the hash of the bearer token is stored. */
@Entity
@Table(name = "auth_session")
class AuthSession {

	@Id
	@Column(name = "token_hash")
	private String tokenHash;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AuthSession() {
	}

	AuthSession(String tokenHash, UUID userId, Instant createdAt) {
		this.tokenHash = tokenHash;
		this.userId = userId;
		this.createdAt = createdAt;
	}

	UUID userId() {
		return userId;
	}

}
