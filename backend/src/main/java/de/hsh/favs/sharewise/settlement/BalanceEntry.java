package de.hsh.favs.sharewise.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "settlement_balance")
class BalanceEntry {

	@Embeddable
	record Key(@Column(name = "group_id") UUID groupId, @Column(name = "member_id") UUID memberId) {
	}

	@EmbeddedId
	private Key id;

	@Column(name = "balance_cents", nullable = false)
	private long balanceCents;

	protected BalanceEntry() {
	}

	Key id() {
		return id;
	}

	long balanceCents() {
		return balanceCents;
	}

}
