package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;

/**
 * Everything needed to report one settlement payment. {@code caller} is the debtor who
 * paid; the note is optional.
 */
public record RecordSettlementPaymentCommand(GroupId groupId, UserId caller, UserId toCreditor, Money amount,
		String note) {

}
