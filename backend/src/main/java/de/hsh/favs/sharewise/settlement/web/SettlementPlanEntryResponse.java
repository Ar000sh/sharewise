package de.hsh.favs.sharewise.settlement.web;

import java.util.UUID;

/**
 * One edge of the settlement plan. {@code amount} and {@code pendingAmount} are decimal
 * strings with two decimals.
 */
record SettlementPlanEntryResponse(UUID fromDebtor, String fromDebtorName, UUID toCreditor, String toCreditorName,
		String amount, String pendingAmount) {

}
