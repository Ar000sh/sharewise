package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** One participant's part of an expense. */
@Embeddable
public class Share {

	@Column(name = "member_id", nullable = false)
	private UUID memberId;

	@Column(name = "share_cents", nullable = false)
	private long shareCents;

	protected Share() {
	}

	public Share(MemberId memberId, Money amount) {
		this.memberId = memberId.value();
		this.shareCents = amount.cents();
	}

	public MemberId memberId() {
		return new MemberId(memberId);
	}

	public Money amount() {
		return Money.eur(shareCents);
	}

}
