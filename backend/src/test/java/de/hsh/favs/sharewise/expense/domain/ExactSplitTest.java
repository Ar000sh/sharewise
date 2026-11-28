package de.hsh.favs.sharewise.expense.domain;

import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.CAROL;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.DAVID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExactSplitTest {

	@Test
	void typedAmountsBecomeTheSharesInTheGivenOrder() {
		// given
		UserId erin = new UserId(UUID.randomUUID());
		List<SplitEntry> entries = List.of(entry(ALICE, "50.00"), entry(BOB, "50.00"), entry(CAROL, "40.00"),
				entry(DAVID, "60.00"), entry(erin, "50.00"));

		// when
		List<ExpenseShare> shares = ExactSplit.split(Money.eur("250.00"), entries);

		// then
		assertThat(shares).containsExactly(new ExpenseShare(ALICE, Money.eur("50.00")),
				new ExpenseShare(BOB, Money.eur("50.00")), new ExpenseShare(CAROL, Money.eur("40.00")),
				new ExpenseShare(DAVID, Money.eur("60.00")), new ExpenseShare(erin, Money.eur("50.00")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.00", "-1.00" })
	void aZeroOrNegativeAmountIsRefused(String amount) {
		// given
		List<SplitEntry> entries = List.of(entry(ALICE, "10.00"), entry(BOB, amount));

		// when / then
		assertThatThrownBy(() -> ExactSplit.split(Money.eur("10.00"), entries)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.AMOUNT_NOT_POSITIVE));
	}

	@Test
	void noEntriesGiveNoShares() {
		assertThat(ExactSplit.split(Money.eur("10.00"), List.of())).isEmpty();
	}

	private static SplitEntry entry(UserId id, String value) {
		return new SplitEntry(id, new BigDecimal(value));
	}

}
