package de.hsh.favs.sharewise.expense.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EqualSplitTest {

	static List<MemberId> members(int n) {
		return IntStream.range(0, n).mapToObj(i -> new MemberId(UUID.randomUUID())).toList();
	}

	static List<Long> cents(List<Share> shares) {
		return shares.stream().map(s -> s.amount().cents()).toList();
	}

	@Test
	void splitsOneHundredEuroAmongFourEvenly() {
		var shares = EqualSplit.split(Money.eur(10_000), members(4));

		assertThat(cents(shares)).containsExactly(2_500L, 2_500L, 2_500L, 2_500L);
	}

	@Test
	void remainderCentsGoToFirstParticipants() {
		var shares = EqualSplit.split(Money.eur(1_000), members(3));

		assertThat(cents(shares)).containsExactly(334L, 333L, 333L);
	}

	@Test
	void oneCentAmongThreeGivesFirstTheCent() {
		var shares = EqualSplit.split(Money.eur(1), members(3));

		assertThat(cents(shares)).containsExactly(1L, 0L, 0L);
	}

	@Test
	void sharesKeepTheOrderOfParticipants() {
		var people = members(3);

		var shares = EqualSplit.split(Money.eur(100), people);

		assertThat(shares.stream().map(Share::memberId).toList()).containsExactlyElementsOf(people);
	}

	@Test
	void sharesAlwaysSumToTotal() {
		var random = new Random(42);
		for (int i = 0; i < 1_000; i++) {
			long total = 1 + random.nextLong(1_000_000_000L);
			int n = 1 + random.nextInt(50);

			var shares = EqualSplit.split(Money.eur(total), members(n));

			assertThat(cents(shares).stream().mapToLong(Long::longValue).sum()).isEqualTo(total);
			assertThat(Math.abs(cents(shares).get(0) - cents(shares).get(n - 1))).isLessThanOrEqualTo(1);
		}
	}

}
