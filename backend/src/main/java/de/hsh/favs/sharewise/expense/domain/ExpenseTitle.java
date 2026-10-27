package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;

/** What an expense was for: trimmed, not blank, at most 200 characters. */
public record ExpenseTitle(String value) {

	private static final int MAX_LENGTH = 200;

	public ExpenseTitle {
		value = value == null ? "" : value.strip();
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "The title must have 1 to " + MAX_LENGTH + " characters");
		}
	}

}
