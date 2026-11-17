package de.hsh.favs.sharewise.expense.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PercentageSplitTest {

	private static List<UserId> people(int count) {
		return IntStream.range(0, count).mapToObj(i -> new UserId(UUID.randomUUID())).toList();
	}

	private static List<SplitEntry> entries(List<UserId> people, String percentages) {
		String[] values = percentages.split(" ");
		return IntStream.range(0, people.size())
			.mapToObj(i -> new SplitEntry(people.get(i), new BigDecimal(values[i])))
			.toList();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "10.00|33.33 33.33 33.34|3.33 3.33 3.34", "10.00|33.34 33.33 33.33|3.34 3.33 3.33",
					"200.00|50 30 20|100.00 60.00 40.00", "10.00|12.5 87.5|1.25 8.75", "0.50|99 1|0.50 0.00",
					"100.00|33.33 33.33 33.34|33.33 33.33 33.34" })
	void leftoverCentsGoToTheLargestDroppedFraction(String total, String percentages, String expected) {
		// given
		List<UserId> people = people(percentages.split(" ").length);

		// when
		List<ExpenseShare> shares = PercentageSplit.split(Money.eur(total), entries(people, percentages));

		// then
		assertThat(shares).extracting(ExpenseShare::participantId).containsExactlyElementsOf(people);
		assertThat(shares).extracting(ExpenseShare::owedShare)
			.containsExactlyElementsOf(Arrays.stream(expected.split(" ")).map(Money::eur).toList());
	}

	@Test
	void percentagesThatDoNotAddUpTo100AreRefused() {
		// given
		List<SplitEntry> entries = entries(people(2), "50 49.99");

		// when / then
		assertThatThrownBy(() -> PercentageSplit.split(Money.eur("10.00"), entries)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PERCENTAGES_NOT_100));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-5" })
	void aZeroOrNegativePercentageIsRefused(String percentage) {
		// given
		List<SplitEntry> entries = entries(people(2), "100 " + percentage);

		// when / then
		assertThatThrownBy(() -> PercentageSplit.split(Money.eur("10.00"), entries)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.AMOUNT_NOT_POSITIVE));
	}

}
