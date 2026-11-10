package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

/**
 * The simplified settlement plan of a group: who pays whom how much so that everyone ends
 * at zero, with the fewest payments (R15). It is a read model computed from the balances;
 * nothing is stored.
 *
 * <p>
 * With up to {@value #EXACT_LIMIT} people that have a non-zero balance the plan is exact:
 * the people are split into the largest possible number k of disjoint zero-sum sets (sets
 * of people whose balances add up to zero; bitmask dynamic programming over subsets), and
 * each set is settled greedily, which needs (people in the set - 1) payments. The total
 * is n - k, the minimum. Above the limit the subset search is too expensive, so the plan
 * is the plain greedy over everyone (largest debtor pays largest creditor); R15 then
 * holds only as "at most n - 1 payments". Ties are broken by user id so the same balances
 * always give the same plan; "ascending user id" means {@link java.util.UUID#compareTo}
 * order, not the order of the hex text. {@link #plannedAmount} on an empty plan returns
 * zero in EUR.
 */
public record SettlementPlan(List<DirectedDebt> simplifiedDebts) {

	/** Largest number of non-zero people for which the exact search runs. */
	static final int EXACT_LIMIT = 16;

	private static final Comparator<UserId> BY_ID = Comparator.comparing(UserId::value);

	public SettlementPlan {
		simplifiedDebts = List.copyOf(simplifiedDebts);
	}

	/**
	 * Builds the plan for the given balances (R15). People at zero are ignored; the
	 * balances are expected to add up to zero and to share one currency.
	 */
	public static SettlementPlan of(List<MemberBalance> balances) {
		if (balances.isEmpty()) {
			return new SettlementPlan(List.of());
		}
		Currency currency = balances.get(0).netBalance().currency();

		// 1. Drop people at zero; sort by user id (same balances, same plan).
		List<MemberBalance> people = balances.stream()
			.filter(balance -> !balance.netBalance().isZero())
			.sorted(Comparator.comparing(MemberBalance::userId, BY_ID))
			.toList();

		// 2. Person i = position i: ids[i] and minorUnits[i] (whole cents, exact).
		int n = people.size();
		UserId[] ids = new UserId[n];
		long[] minorUnits = new long[n];
		for (int i = 0; i < n; i++) {
			ids[i] = people.get(i).userId();
			minorUnits[i] = people.get(i).netBalance().minorUnits();
		}

		// 3. Split people into the most sets that add up to zero (R15). Above the
		// limit that search is too expensive: everybody is one set.
		List<List<Integer>> zeroSumSets = (n <= EXACT_LIMIT) ? splitIntoZeroSumSets(minorUnits)
				: List.of(allIndexes(n));

		// 4. In each set the largest debtor pays the largest creditor, until zero.
		// The sets are disjoint, so they can share the array.
		List<DirectedDebt> edges = new ArrayList<>();
		for (List<Integer> zeroSumSet : zeroSumSets) {
			settleGreedily(zeroSumSet, ids, minorUnits, currency, edges);
		}

		// 5. List payments by creditor, then debtor: the API order never changes.
		edges
			.sort(Comparator.comparing(DirectedDebt::toCreditor, BY_ID).thenComparing(DirectedDebt::fromDebtor, BY_ID));
		return new SettlementPlan(edges);
	}

	/**
	 * The amount debtor pays creditor in this plan, or zero when the pair has no edge.
	 */
	public Money plannedAmount(UserId debtor, UserId creditor) {
		return simplifiedDebts.stream()
			.filter(debt -> debt.fromDebtor().equals(debtor) && debt.toCreditor().equals(creditor))
			.map(DirectedDebt::amount)
			.findFirst()
			.orElseGet(() -> Money.zero(simplifiedDebts.isEmpty() ? Currency.getInstance("EUR")
					: simplifiedDebts.get(0).amount().currency()));
	}

	private static List<Integer> allIndexes(int n) {
		List<Integer> all = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			all.add(i);
		}
		return all;
	}

	/**
	 * The partition of all people into the largest number of zero-sum sets. {@code
	 * best[mask]} is the most zero-sum prefixes any ordering of {@code mask} can have;
	 * following the chosen last person from the full set downwards yields one such
	 * ordering, whose zero-sum prefixes are the boundaries between the sets.
	 */
	private static List<List<Integer>> splitIntoZeroSumSets(long[] minorUnits) {
		int n = minorUnits.length;
		if (n == 0) {
			return List.of();
		}
		int full = (1 << n) - 1;
		long[] sum = new long[full + 1];
		int[] best = new int[full + 1];
		int[] choice = new int[full + 1];
		for (int mask = 1; mask <= full; mask++) {
			int low = Integer.numberOfTrailingZeros(mask);
			sum[mask] = sum[mask & (mask - 1)] + minorUnits[low];
			int bestValue = -1;
			for (int i = 0; i < n; i++) {
				if ((mask & (1 << i)) != 0 && best[mask & ~(1 << i)] > bestValue) {
					bestValue = best[mask & ~(1 << i)];
					choice[mask] = i; // strictly greater: the lowest index wins ties
				}
			}
			best[mask] = bestValue + (sum[mask] == 0 ? 1 : 0);
		}

		List<List<Integer>> zeroSumSets = new ArrayList<>();
		List<Integer> currentSet = new ArrayList<>();
		int mask = full;
		while (mask != 0) {
			if (sum[mask] == 0 && !currentSet.isEmpty()) {
				zeroSumSets.add(currentSet);
				currentSet = new ArrayList<>();
			}
			int i = choice[mask];
			currentSet.add(i);
			mask &= ~(1 << i);
		}
		zeroSumSets.add(currentSet);
		return zeroSumSets;
	}

	/**
	 * Largest debtor pays largest creditor until the set is at zero; ties by user id.
	 */
	private static void settleGreedily(List<Integer> zeroSumSet, UserId[] ids, long[] minorUnits, Currency currency,
			List<DirectedDebt> edges) {
		Comparator<Integer> byIdAsc = Comparator.comparing(i -> ids[i], BY_ID);
		while (true) {
			Integer debtor = zeroSumSet.stream()
				.filter(i -> minorUnits[i] < 0)
				.min(Comparator.<Integer>comparingLong(i -> minorUnits[i]).thenComparing(byIdAsc))
				.orElse(null);
			Integer creditor = zeroSumSet.stream()
				.filter(i -> minorUnits[i] > 0)
				.min(Comparator.<Integer>comparingLong(i -> -minorUnits[i]).thenComparing(byIdAsc))
				.orElse(null);
			if (debtor == null || creditor == null) {
				return;
			}
			long amount = Math.min(-minorUnits[debtor], minorUnits[creditor]);
			minorUnits[debtor] += amount;
			minorUnits[creditor] -= amount;
			edges.add(new DirectedDebt(ids[debtor], ids[creditor], Money.ofMinorUnits(amount, currency)));
		}
	}

}
