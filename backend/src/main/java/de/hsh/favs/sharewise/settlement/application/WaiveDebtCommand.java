package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;

/**
 * Everything needed to waive part of a debt. {@code caller} is the creditor who forgives
 * it; the note is the required reason.
 */
public record WaiveDebtCommand(GroupId groupId, UserId caller, UserId fromDebtor, Money amount, String note) {

}
