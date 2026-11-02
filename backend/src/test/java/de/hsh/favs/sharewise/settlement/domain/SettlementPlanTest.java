package de.hsh.favs.sharewise.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The rules of the settlement plan (R15): the fewest payments that settle everyone. No
 * Spring, no database.
 */
class SettlementPlanTest {

	private static final Currency EUR = Currency.getInstance("EUR");

	UserId a = id(1);

	UserId b = id(2);

	UserId c = id(3);

	UserId d = id(4);

	UserId e = id(5);

	UserId f = id(6);

	private static UserId id(int n) {
		return new UserId(new UUID(0, n));
	}

	private static MemberBalance balance(UserId user, long cents) {
		return new MemberBalance(user, Money.ofMinorUnits(cents, EUR));
	}

	private static Money sumOfEdges(SettlementPlan plan) {
		return plan.simplifiedDebts().stream().map(DirectedDebt::amount).reduce(Money.zero(EUR), Money::plus);
	}

	@Test
	void mallorcaBalancesGiveThreePaymentsToAlice() {
		// given
		UserId alice = id(10);
		UserId bob = id(11);
		UserId carol = id(12);
		UserId david = id(13);

		// when
		SettlementPlan plan = SettlementPlan
			.of(List.of(balance(alice, 63000), balance(bob, -9000), balance(carol, -27000), balance(david, -27000)));

		// then
		assertThat(plan.simplifiedDebts()).hasSize(3);
		assertThat(plan.edge(carol, alice)).isEqualTo(Money.eur("270.00"));
		assertThat(plan.edge(david, alice)).isEqualTo(Money.eur("270.00"));
		assertThat(plan.edge(bob, alice)).isEqualTo(Money.eur("90.00"));
		assertThat(sumOfEdges(plan)).isEqualTo(Money.eur("630.00"));
	}

	@Test
	void twoIndependentPairsAreSettledSeparatelyNotThroughOnePerson() {
		// when
		SettlementPlan plan = SettlementPlan
			.of(List.of(balance(a, 1000), balance(b, -1000), balance(c, 2000), balance(d, -2000)));

		// then
		assertThat(plan.simplifiedDebts()).hasSize(2);
		assertThat(plan.edge(b, a)).isEqualTo(Money.eur("10.00"));
		assertThat(plan.edge(d, c)).isEqualTo(Money.eur("20.00"));
	}

	@Test
	void threeZeroSumSubgroupsNeedExactlyThreePaymentsNotFive() {
		// given A +5, B +5, C -5, D -5, E +3, F -3: three pairs hide in six people
		// when
		SettlementPlan plan = SettlementPlan.of(List.of(balance(a, 500), balance(b, 500), balance(c, -500),
				balance(d, -500), balance(e, 300), balance(f, -300)));

		// then
		assertThat(plan.simplifiedDebts()).hasSize(3);
		assertThat(sumOfEdges(plan)).isEqualTo(Money.eur("13.00"));
		assertThat(plan.edge(f, e)).isEqualTo(Money.eur("3.00"));
	}

	@Test
	void theNumberOfPaymentsIsNeverMoreThanPeopleMinusSubgroups() {
		// given random zero-sum balances of up to 8 people, fixed seed
		Random random = new Random(20261102);
		for (int round = 0; round < 300; round++) {
			int people = 2 + random.nextInt(7);
			List<MemberBalance> balances = randomZeroSum(random, people);
			long nonZero = balances.stream().filter(x -> !x.netBalance().isZero()).count();

			// when
			SettlementPlan plan = SettlementPlan.of(balances);

			// then
			assertThat(plan.simplifiedDebts().size()).isLessThanOrEqualTo((int) Math.max(0, nonZero - 1));
			assertSettles(balances, plan);
		}
	}

	@Test
	void tiesAreBrokenByUserIdSoTwoCallsGiveTheSamePlan() {
		// given two debtors of the same amount and two creditors of the same amount
		List<MemberBalance> balances = List.of(balance(d, -1000), balance(c, -1000), balance(b, 1000),
				balance(a, 1000));

		// when
		SettlementPlan first = SettlementPlan.of(balances);
		SettlementPlan second = SettlementPlan
			.of(List.of(balances.get(3), balances.get(2), balances.get(1), balances.get(0)));

		// then
		assertThat(first).isEqualTo(second);
		assertThat(first.simplifiedDebts()).containsExactly(new DirectedDebt(c, a, Money.eur("10.00")),
				new DirectedDebt(d, b, Money.eur("10.00")));
	}

	@Test
	void everyoneAtZeroOrAnEmptyListGivesAnEmptyPlan() {
		// when / then
		assertThat(SettlementPlan.of(List.of()).simplifiedDebts()).isEmpty();
		assertThat(SettlementPlan.of(List.of(balance(a, 0), balance(b, 0))).simplifiedDebts()).isEmpty();
	}

	@Test
	void theSumOfEdgesEqualsTheSumOfPositiveBalancesAndNoBalanceIsChanged() {
		// given
		List<MemberBalance> balances = List.of(balance(a, 1234), balance(b, 766), balance(c, -1500), balance(d, -500));

		// when
		SettlementPlan plan = SettlementPlan.of(balances);

		// then
		assertThat(sumOfEdges(plan)).isEqualTo(Money.eur("20.00"));
		assertSettles(balances, plan);
		assertThat(balances.get(0).netBalance()).isEqualTo(Money.eur("12.34"));
	}

	@Test
	void moreThanSixteenPeopleFallBackToGreedyAndStillSettleEveryone() {
		// given 18 people with non-zero balances that add up to zero
		Random random = new Random(7);
		List<MemberBalance> balances = randomZeroSum(random, 18);
		assertThat(balances.stream().filter(x -> !x.netBalance().isZero())).hasSizeGreaterThan(16);

		// when
		SettlementPlan plan = SettlementPlan.of(balances);

		// then
		assertSettles(balances, plan);
		assertThat(plan.simplifiedDebts().size()).isLessThanOrEqualTo(17);
	}

	@Test
	void edgeIsZeroForAPairThatIsNotInThePlan() {
		// given
		SettlementPlan plan = SettlementPlan.of(List.of(balance(a, 500), balance(b, -500)));

		// when / then
		assertThat(plan.edge(b, a)).isEqualTo(Money.eur("5.00"));
		assertThat(plan.edge(a, b)).isEqualTo(Money.eur("0.00"));
		assertThat(plan.edge(c, a)).isEqualTo(Money.eur("0.00"));
	}

	private List<MemberBalance> randomZeroSum(Random random, int people) {
		List<MemberBalance> balances = new ArrayList<>();
		long sum = 0;
		for (int i = 0; i < people - 1; i++) {
			long cents = random.nextInt(21) - 10;
			if (cents == 0) {
				cents = 1 + i % 3;
			}
			cents = cents * 100;
			sum += cents;
			balances.add(balance(id(100 + i), cents));
		}
		balances.add(balance(id(100 + people - 1), -sum));
		return balances;
	}

	private void assertSettles(List<MemberBalance> balances, SettlementPlan plan) {
		Map<UserId, Long> remaining = new HashMap<>();
		balances.forEach(x -> remaining.put(x.userId(), x.netBalance().minorUnits()));
		for (DirectedDebt debt : plan.simplifiedDebts()) {
			assertThat(debt.amount().isPositive()).isTrue();
			remaining.merge(debt.fromDebtor(), debt.amount().minorUnits(), Long::sum);
			remaining.merge(debt.toCreditor(), -debt.amount().minorUnits(), Long::sum);
		}
		assertThat(remaining.values()).containsOnly(0L);
		long positives = balances.stream().mapToLong(x -> Math.max(0, x.netBalance().minorUnits())).sum();
		assertThat(sumOfEdges(plan).minorUnits()).isEqualTo(positives);
	}

}
