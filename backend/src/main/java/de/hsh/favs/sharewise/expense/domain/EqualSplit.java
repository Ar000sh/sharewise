package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.ArrayList;
import java.util.List;

/**
 * The EQUAL split strategy (R4): the total divided by the number of participants, with
 * the leftover smallest units (cents) given one each to the first participants in the
 * order given, so the shares always add up to the total exactly.
 */
public final class EqualSplit {

	private EqualSplit() {
	}

	/**
	 * @throws DomainException AMOUNT_NOT_POSITIVE when the total is zero or negative
	 */
	public static List<ExpenseShare> split(Money total, List<UserId> participants) {
		if (!total.isPositive()) {
			throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE, "Amount must be greater than zero");
		}
		if (participants.isEmpty()) {
			return List.of();
		}
		int count = participants.size();
		long base = total.minorUnits() / count;
		long remainder = total.minorUnits() % count;
		var shares = new ArrayList<ExpenseShare>(count);
		for (int i = 0; i < count; i++) {
			long units = base + (i < remainder ? 1 : 0);
			shares.add(new ExpenseShare(participants.get(i), Money.ofMinorUnits(units, total.currency())));
		}
		return List.copyOf(shares);
	}

}
