package de.hsh.favs.sharewise.expense.api;

import java.util.UUID;

/** Identity of an expense. Lives in {@code api} because events carry it. */
public record ExpenseId(UUID value) {

}
