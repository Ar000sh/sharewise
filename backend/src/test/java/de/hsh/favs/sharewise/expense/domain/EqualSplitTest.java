package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.UserId;
import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.shared.Money;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EqualSplitTest {

	static List<UserId> members(int n) {
		return IntStream.range(0, n).mapToObj(i -> new UserId(UUID.randomUUID())).toList();
	}

	static List<Long> cents(List<Share> shares) {
		return shares.stream().map(s -> s.amount().cents()).toList();
	}

	@Test
	void splitsOneHundredEuroAmongFourEvenly() {
		// when
		var shares = EqualSplit.split(Money.eur(10_000), members(4));

		// then
		assertThat(cents(shares)).containsExactly(2_500L, 2_500L, 2_500L, 2_500L);
	}

	@Test
	void remainderCentsGoToFirstParticipants() {
		// when
		var shares = EqualSplit.split(Money.eur(1_000), members(3));

		// then
		assertThat(cents(shares)).containsExactly(334L, 333L, 333L);
	}

	@Test
	void oneCentAmongThreeGivesFirstTheCent() {
		// when
		var shares = EqualSplit.split(Money.eur(1), members(3));

		// then
		assertThat(cents(shares)).containsExactly(1L, 0L, 0L);
	}

	@Test
	void sharesKeepTheOrderOfParticipants() {
		// given
		var people = members(3);

		// when
		var shares = EqualSplit.split(Money.eur(100), people);

		// then
		assertThat(shares.stream().map(Share::memberId).toList()).containsExactlyElementsOf(people);
	}

	@Test
	void sharesAlwaysSumToTotalAndDifferByAtMostOneCent() {
		// given
		var random = new Random(42);
		for (int i = 0; i < 1_000; i++) {
			long total = 1 + random.nextLong(1_000_000_000L);
			int n = 1 + random.nextInt(50);

			// when
			var cents = cents(EqualSplit.split(Money.eur(total), members(n)));

			// then
			assertThat(cents.stream().mapToLong(Long::longValue).sum()).isEqualTo(total);
			assertThat(cents.get(0) - cents.get(n - 1)).isBetween(0L, 1L);
		}
	}

}
