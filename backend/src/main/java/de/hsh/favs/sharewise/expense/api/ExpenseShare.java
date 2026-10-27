package de.hsh.favs.sharewise.expense.api;

import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;

/**
 * What one participant owes for an expense. The shares of an expense add up to its total
 * amount.
 */
public record ExpenseShare(UserId participantId, Money owedShare) {

}
