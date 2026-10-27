package de.hsh.favs.sharewise.group.domain;

import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One person's place in one group: the role and whether they are still in. Part of the
 * {@link Group} aggregate, only created and changed through it.
 */
@Entity
@Table(name = "memberships")
public class Membership {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GroupRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MembershipStatus status;

	/**
	 * Kept up to date from the settlement context (later exercise); a member may only
	 * leave at zero balance.
	 */
	@Column(name = "has_zero_balance", nullable = false)
	private boolean hasZeroBalance;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	protected Membership() {
	}

	Membership(UserId userId, GroupRole role, Instant joinedAt) {
		this.id = UUID.randomUUID();
		this.userId = userId.value();
		this.role = role;
		this.status = MembershipStatus.ACTIVE;
		this.hasZeroBalance = true;
		this.joinedAt = joinedAt;
	}

	public UserId userId() {
		return new UserId(userId);
	}

	public GroupRole role() {
		return role;
	}

	public MembershipStatus status() {
		return status;
	}

	public boolean hasZeroBalance() {
		return hasZeroBalance;
	}

	public Instant joinedAt() {
		return joinedAt;
	}

	public boolean isActiveMember() {
		return status == MembershipStatus.ACTIVE;
	}

}
