package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;

/**
 * One payment of a settlement plan: {@code fromDebtor} pays {@code amount} to
 * {@code toCreditor} (R15). The amount is always positive.
 */
public record DirectedDebt(UserId fromDebtor, UserId toCreditor, Money amount) {
}
