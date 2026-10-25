package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.group.api.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "members")
class Member {

	@Id
	private UUID id;

	@Column(name = "member_id", nullable = false)
	private UUID memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	protected Member() {
	}

	Member(UUID memberId, Role role, Instant joinedAt) {
		this.id = UUID.randomUUID();
		this.memberId = memberId;
		this.role = role;
		this.joinedAt = joinedAt;
	}

	UUID memberId() {
		return memberId;
	}

	Role role() {
		return role;
	}

	Instant joinedAt() {
		return joinedAt;
	}

}
