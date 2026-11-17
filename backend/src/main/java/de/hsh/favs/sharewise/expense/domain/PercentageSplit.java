package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The PERCENTAGE split strategy (R4): each share is the total times its percentage,
 * floored to cents; the leftover cents go one each to the entries with the largest
 * dropped fraction (ties by list order), so the shares add up to the total exactly
 * (design D4).
 */
public final class PercentageSplit {

	private static final BigDecimal HUNDRED = new BigDecimal(100);

	private static final BigDecimal CENT = new BigDecimal("0.01");

	private PercentageSplit() {
	}

	/**
	 * @throws DomainException AMOUNT_NOT_POSITIVE when a percentage is zero or negative
	 * (R18), PERCENTAGES_NOT_100 when the percentages do not add up to exactly 100
	 */
	public static List<ExpenseShare> split(Money total, List<SplitEntry> entries) {
		BigDecimal sum = BigDecimal.ZERO;
		for (SplitEntry entry : entries) {
			if (entry.value().signum() <= 0) {
				throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE, "Every percentage must be greater than zero");
			}
			sum = sum.add(entry.value());
		}
		if (!entries.isEmpty() && sum.compareTo(HUNDRED) != 0) {
			throw new DomainException(ErrorCode.PERCENTAGES_NOT_100,
					"The percentages add up to " + sum + ", not to 100");
		}

		// exact = total * pct / 100 (exact in BigDecimal, no rounding yet); floor to
		// cents
		int count = entries.size();
		var exact = new ArrayList<BigDecimal>(count);
		var floors = new ArrayList<BigDecimal>(count);
		BigDecimal floorSum = BigDecimal.ZERO;
		for (SplitEntry entry : entries) {
			BigDecimal share = total.amount().multiply(entry.value()).movePointLeft(2);
			BigDecimal floor = share.setScale(2, RoundingMode.FLOOR);
			exact.add(share);
			floors.add(floor);
			floorSum = floorSum.add(floor);
		}

		// leftover = total - sum(floors), in cents, always < entries.size()
		int leftover = total.amount().subtract(floorSum).movePointRight(2).intValueExact();
		// indices sorted by (exact - floor) descending, ties by index ascending (a
		// stable sort); the first `leftover` of them get +0.01 (R4, design D4)
		List<Integer> byDroppedFraction = IntStream.range(0, count)
			.boxed()
			.sorted(Comparator.comparing((Integer i) -> exact.get(i).subtract(floors.get(i))).reversed())
			.toList();
		for (int rank = 0; rank < leftover; rank++) {
			int index = byDroppedFraction.get(rank);
			floors.set(index, floors.get(index).add(CENT));
		}

		return IntStream.range(0, count)
			.mapToObj(i -> new ExpenseShare(entries.get(i).participantId(), new Money(floors.get(i), total.currency())))
			.toList();
	}

}
