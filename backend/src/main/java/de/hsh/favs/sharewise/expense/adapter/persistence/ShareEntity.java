package de.hsh.favs.sharewise.expense.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
class ShareEntity {

	@Column(name = "member_id", nullable = false)
	UUID memberId;

	@Column(name = "share_cents", nullable = false)
	long shareCents;

	protected ShareEntity() {
	}

	ShareEntity(UUID memberId, long shareCents) {
		this.memberId = memberId;
		this.shareCents = shareCents;
	}

}
