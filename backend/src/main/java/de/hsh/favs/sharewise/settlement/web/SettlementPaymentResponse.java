package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.settlement.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A settlement payment as the API shows it. {@code amount} is a decimal string with two
 * decimals; {@code note} and {@code resolvedAt} are null while unset.
 */
record SettlementPaymentResponse(UUID id, UUID groupId, UUID fromDebtor, String fromDebtorName, UUID toCreditor,
		String toCreditorName, String amount, PaymentStatus status, String note, Instant createdAt,
		Instant resolvedAt) {

}
