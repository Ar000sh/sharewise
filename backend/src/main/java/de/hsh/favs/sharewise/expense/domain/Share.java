package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;

/** One participant's part of an expense. */
public record Share(MemberId memberId, Money amount) {
}
