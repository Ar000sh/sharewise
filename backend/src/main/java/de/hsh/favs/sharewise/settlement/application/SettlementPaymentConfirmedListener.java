package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the confirmation of a settlement payment into a call on the ledger service (R16,
 * design D7). Asynchronous, after the commit.
 */
@Component
class SettlementPaymentConfirmedListener {

	private final GroupLedgerService ledgers;

	SettlementPaymentConfirmedListener(GroupLedgerService ledgers) {
		this.ledgers = ledgers;
	}

	@ApplicationModuleListener
	void on(SettlementPaymentConfirmed event) {
		ledgers.bookSettlementPayment(event);
	}

}
