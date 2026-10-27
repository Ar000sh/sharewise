package de.hsh.favs.sharewise.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A signed-in browser session. Infrastructure of the sign-in, not part of the
 * {@link Account} aggregate: only the hash of the bearer token is stored, so a leaked
 * table cannot be replayed.
 */
@Entity
@Table(name = "auth_session")
public class AuthSession {

	@Id
	@Column(name = "token_hash")
	private String tokenHash;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AuthSession() {
	}

	public AuthSession(String tokenHash, UUID userId, Instant createdAt) {
		this.tokenHash = tokenHash;
		this.userId = userId;
		this.createdAt = createdAt;
	}

	public UUID userId() {
		return userId;
	}

}
