package de.hsh.favs.sharewise.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
class User {

	@Id
	private UUID id;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	/** Lower-case. Null for the seeded dev users, who cannot sign in. */
	private String email;

	@Column(name = "password_hash")
	private String passwordHash;

	protected User() {
	}

	User(UUID id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	static User signUp(String email, String displayName, String passwordHash) {
		User user = new User(UUID.randomUUID(), displayName);
		user.email = email;
		user.passwordHash = passwordHash;
		return user;
	}

	UUID id() {
		return id;
	}

	String displayName() {
		return displayName;
	}

	String passwordHash() {
		return passwordHash;
	}

}
