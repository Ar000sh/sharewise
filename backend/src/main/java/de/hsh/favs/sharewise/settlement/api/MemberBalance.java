package de.hsh.favs.sharewise.settlement.api;

import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;

/**
 * What one member is owed (positive) or owes (negative) in a group, summed over
 * everything booked so far. Names are not part of it; ask the account module for the
 * profile.
 */
public record MemberBalance(UserId userId, Money netBalance) {

}
