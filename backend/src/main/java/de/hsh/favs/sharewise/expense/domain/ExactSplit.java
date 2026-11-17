package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import java.util.List;

/**
 * The EXACT split strategy (R4): each typed amount becomes that participant's share, in
 * the order given. Whether the amounts add up to the total is not checked here but once,
 * in {@link Expense#record}, for every strategy (design D5).
 */
public final class ExactSplit {

	private ExactSplit() {
	}

	/**
	 * @throws DomainException AMOUNT_NOT_POSITIVE when an amount is zero or negative
	 * (R18: a recorder with 0.00 would undo the rule that the recorder takes part)
	 */
	public static List<ExpenseShare> split(Money total, List<SplitEntry> entries) {
		return entries.stream().map(entry -> {
			if (entry.value().signum() <= 0) {
				throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE, "Every amount must be greater than zero");
			}
			return new ExpenseShare(entry.participantId(), new Money(entry.value(), total.currency()));
		}).toList();
	}

}
