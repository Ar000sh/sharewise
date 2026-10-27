package de.hsh.favs.sharewise.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An amount of money in one currency. The amount always has exactly the number of
 * decimals the currency has (EUR: 2), so equal amounts are equal records. Never built
 * from a floating point number.
 *
 * @throws DomainException with {@link ErrorCode#BAD_REQUEST} when the amount has more
 * decimals than the currency
 */
public record Money(BigDecimal amount, Currency currency) {

	private static final Currency EUR = Currency.getInstance("EUR");

	private static final Pattern DECIMAL = Pattern.compile("^-?\\d{1,15}(\\.\\d+)?$");

	public Money {
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
		try {
			amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException tooManyDecimals) {
			throw new DomainException(ErrorCode.BAD_REQUEST,
					"At most " + currency.getDefaultFractionDigits() + " decimals are allowed for " + currency);
		}
	}

	public static Money eur(BigDecimal amount) {
		return new Money(amount, EUR);
	}

	/**
	 * Parses a plain decimal such as {@code 900}, {@code 900.5} or {@code 900.00}:
	 * digits, an optional minus sign and an optional point. No thousands separators, no
	 * exponent.
	 */
	public static Money eur(String text) {
		if (text == null || !DECIMAL.matcher(text).matches()) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "Not a valid amount");
		}
		return eur(new BigDecimal(text));
	}

	/** The amount in the smallest unit of the currency, for example cents. */
	public static Money ofMinorUnits(long units, Currency currency) {
		return new Money(BigDecimal.valueOf(units, currency.getDefaultFractionDigits()), currency);
	}

	public static Money zero(Currency currency) {
		return ofMinorUnits(0, currency);
	}

	/** The amount in the smallest unit of the currency, for example cents. */
	public long minorUnits() {
		return amount.unscaledValue().longValueExact();
	}

	public Money plus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.add(other.amount), currency);
	}

	public Money minus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.subtract(other.amount), currency);
	}

	public Money negate() {
		return new Money(amount.negate(), currency);
	}

	public boolean isPositive() {
		return amount.signum() > 0;
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isZero() {
		return amount.signum() == 0;
	}

	private void requireSameCurrency(Money other) {
		if (!currency.equals(other.currency)) {
			throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
		}
	}

}
