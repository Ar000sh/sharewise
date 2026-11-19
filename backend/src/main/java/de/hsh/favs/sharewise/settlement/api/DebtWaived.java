package de.hsh.favs.sharewise.settlement.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * The creditor forgave part of a debt, which takes effect at once (R17, D8). The
 * free-text note is not part of the event.
 */
public record DebtWaived(SettlementPaymentId paymentId, GroupId groupId, UserId fromDebtor, UserId toCreditor,
		Money amount, Instant occurredAt) {

}
