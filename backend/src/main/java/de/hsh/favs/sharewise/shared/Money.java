package de.hsh.favs.sharewise.shared;

import java.util.Currency;

/** An amount in minor units (cents). Never use floating point for money. */
public record Money(long cents, Currency currency) {

	private static final Currency EUR = Currency.getInstance("EUR");

	public static Money eur(long cents) {
		return new Money(cents, EUR);
	}

	public Money plus(Money other) {
		requireSameCurrency(other);
		return new Money(Math.addExact(cents, other.cents), currency);
	}

	public Money minus(Money other) {
		requireSameCurrency(other);
		return new Money(Math.subtractExact(cents, other.cents), currency);
	}

	public boolean isPositive() {
		return cents > 0;
	}

	private void requireSameCurrency(Money other) {
		if (!currency.equals(other.currency)) {
			throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
		}
	}

}
