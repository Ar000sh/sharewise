package de.hsh.favs.sharewise.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void plusAndMinusKeepCurrency() {
		Money sum = Money.eur(1000).plus(Money.eur(250));
		Money diff = Money.eur(1000).minus(Money.eur(250));

		assertThat(sum).isEqualTo(Money.eur(1250));
		assertThat(diff).isEqualTo(Money.eur(750));
		assertThat(sum.currency()).isEqualTo(Currency.getInstance("EUR"));
	}

	@Test
	void isPositiveFalseForZeroAndNegative() {
		assertThat(Money.eur(1).isPositive()).isTrue();
		assertThat(Money.eur(0).isPositive()).isFalse();
		assertThat(Money.eur(-5).isPositive()).isFalse();
	}

	@Test
	void mixingCurrenciesIsRejected() {
		Money usd = new Money(100, Currency.getInstance("USD"));

		assertThatThrownBy(() -> Money.eur(100).plus(usd)).isInstanceOf(IllegalArgumentException.class);
	}

}
