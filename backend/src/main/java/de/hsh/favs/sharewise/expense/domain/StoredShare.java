package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * How an {@link ExpenseShare} is stored: one row per participant. The currency is the
 * expense's.
 */
@Embeddable
class StoredShare {

	@Column(name = "participant_id", nullable = false)
	private UUID participantId;

	@Column(name = "owed_share", nullable = false, precision = 19, scale = 2)
	private BigDecimal owedShare;

	protected StoredShare() {
	}

	StoredShare(ExpenseShare share) {
		this.participantId = share.participantId().value();
		this.owedShare = share.owedShare().amount();
	}

	ExpenseShare toShare(Currency currency) {
		return new ExpenseShare(new UserId(participantId), new Money(owedShare, currency));
	}

}
