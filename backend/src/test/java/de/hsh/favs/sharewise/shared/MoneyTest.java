package de.hsh.favs.sharewise.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

	static final Currency EUR = Currency.getInstance("EUR");

	@Test
	void plusAndMinusKeepTheCurrencyAndAreExact() {
		Money sum = Money.eur("10.10").plus(Money.eur("0.20"));
		Money diff = Money.eur("10.00").minus(Money.eur("2.50"));

		assertThat(sum).isEqualTo(Money.eur("10.30"));
		assertThat(diff).isEqualTo(Money.eur("7.50"));
		assertThat(sum.currency()).isEqualTo(EUR);
	}

	@Test
	void theAmountAlwaysHasTheCurrencysDecimalsSoEqualAmountsAreEqual() {
		assertThat(Money.eur("900")).isEqualTo(Money.eur("900.00")).isEqualTo(Money.eur(new BigDecimal("900.0")));
		assertThat(Money.eur("900").amount().toPlainString()).isEqualTo("900.00");
	}

	@Test
	void signTests() {
		assertThat(Money.eur("0.01").isPositive()).isTrue();
		assertThat(Money.eur("0").isPositive()).isFalse();
		assertThat(Money.eur("0").isZero()).isTrue();
		assertThat(Money.eur("-5").isNegative()).isTrue();
		assertThat(Money.eur("5").negate()).isEqualTo(Money.eur("-5.00"));
	}

	@Test
	void minorUnitsAreTheCentsAndConvertBothWays() {
		assertThat(Money.eur("900.00").minorUnits()).isEqualTo(90000);
		assertThat(Money.eur("-0.07").minorUnits()).isEqualTo(-7);
		assertThat(Money.ofMinorUnits(123456, EUR)).isEqualTo(Money.eur("1234.56"));
	}

	@Test
	void moreDecimalsThanTheCurrencyHasAreRejectedNotRounded() {
		assertThatThrownBy(() -> Money.eur("1.005")).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "abc", "1,5", "1.000,50", "1e3", "--1", "1.", ".5", "+1" })
	void somethingThatIsNotAPlainDecimalIsRejected(String text) {
		assertThatThrownBy(() -> Money.eur(text)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST));
	}

	@Test
	void mixingCurrenciesIsRejected() {
		Money usd = new Money(new BigDecimal("1.00"), Currency.getInstance("USD"));

		assertThatThrownBy(() -> Money.eur("1.00").plus(usd)).isInstanceOf(IllegalArgumentException.class);
	}

}
