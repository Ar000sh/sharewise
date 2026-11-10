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
	 * Kept up to date from the settlement ledger through
	 * {@code MemberBalanceStatusChanged} (D12); a member may only leave at zero balance
	 * (R11).
	 */
	@Column(name = "has_zero_balance", nullable = false)
	private boolean hasZeroBalance;

	/**
	 * When the ledger event that last set {@link #hasZeroBalance} occurred, or null
	 * before the first one. Events arrive asynchronously and possibly out of order; an
	 * older one must not override a newer one.
	 */
	@Column(name = "balance_status_at")
	private Instant balanceStatusAt;

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

	void promoteToAdmin() {
		this.role = GroupRole.ADMIN;
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

	/**
	 * Sets the flag unless a newer status is already stored. Equal instants apply, so a
	 * later event with the same timestamp (a frozen clock) still wins. Called by the root
	 * only.
	 */
	void updateBalanceStatus(boolean hasZeroBalance, Instant occurredAt) {
		if (balanceStatusAt != null && occurredAt.isBefore(balanceStatusAt)) {
			return;
		}
		this.hasZeroBalance = hasZeroBalance;
		this.balanceStatusAt = occurredAt;
	}

}
