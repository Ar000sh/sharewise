package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.UserId;
import java.math.BigDecimal;

/**
 * What the recorder typed for one participant: an amount (EXACT) or a percentage
 * (PERCENTAGE). The value is null for an EQUAL split, which types nothing.
 */
public record SplitEntry(UserId participantId, BigDecimal value) {

}
