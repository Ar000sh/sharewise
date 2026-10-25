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

	protected User() {
	}

	User(UUID id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	UUID id() {
		return id;
	}

	String displayName() {
		return displayName;
	}

}
