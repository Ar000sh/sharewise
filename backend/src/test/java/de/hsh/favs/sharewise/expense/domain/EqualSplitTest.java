package de.hsh.favs.sharewise.expense.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EqualSplitTest {

	private static List<UserId> people(int count) {
		return IntStream.range(0, count).mapToObj(i -> new UserId(UUID.randomUUID())).toList();
	}

	@Test
	void anEvenAmountIsDividedEqually() {
		// given
		List<UserId> four = people(4);

		// when
		List<ExpenseShare> shares = EqualSplit.split(Money.eur("900.00"), four);

		// then
		assertThat(shares).extracting(ExpenseShare::owedShare).containsOnly(Money.eur("225.00"));
		assertThat(shares).extracting(ExpenseShare::participantId).containsExactlyElementsOf(four);
	}

	@Test
	void theLeftoverCentsGoOneEachToTheFirstParticipantsInTheOrderGiven() {
		// when 100.00 is split three ways
		List<ExpenseShare> shares = EqualSplit.split(Money.eur("100.00"), people(3));

		// then
		assertThat(shares).extracting(ExpenseShare::owedShare)
			.containsExactly(Money.eur("33.34"), Money.eur("33.33"), Money.eur("33.33"));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 3, 7, 13, 99 })
	void theSharesAlwaysAddUpToTheTotalExactly(int count) {
		// given an amount that divides badly
		Money total = Money.eur("1000.01");

		// when
		Money sum = EqualSplit.split(total, people(count))
			.stream()
			.map(ExpenseShare::owedShare)
			.reduce(Money.zero(total.currency()), Money::plus);

		// then
		assertThat(sum).isEqualTo(total);
	}

	@Test
	void noParticipantsGivesNoShares() {
		assertThat(EqualSplit.split(Money.eur("10.00"), List.of())).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.00", "-5.00" })
	void aTotalThatIsNotPositiveIsRefused(String amount) {
		assertThatThrownBy(() -> EqualSplit.split(Money.eur(amount), people(2))).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.AMOUNT_NOT_POSITIVE));
	}

}
