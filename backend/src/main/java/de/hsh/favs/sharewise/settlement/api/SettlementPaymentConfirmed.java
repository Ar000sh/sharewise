package de.hsh.favs.sharewise.settlement.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * The creditor confirmed a payment, so the debt shrinks by the amount (R16). The
 * free-text note is not part of the event.
 */
public record SettlementPaymentConfirmed(SettlementPaymentId paymentId, GroupId groupId, UserId fromDebtor,
		UserId toCreditor, Money amount, Instant occurredAt) {

}
