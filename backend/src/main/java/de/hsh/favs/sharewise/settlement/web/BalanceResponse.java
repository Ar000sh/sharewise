package de.hsh.favs.sharewise.settlement.web;

import java.util.UUID;

/**
 * One member's balance. {@code netBalance} is a decimal string, positive when the person
 * is owed money.
 */
record BalanceResponse(UUID userId, String displayName, String netBalance) {

}
