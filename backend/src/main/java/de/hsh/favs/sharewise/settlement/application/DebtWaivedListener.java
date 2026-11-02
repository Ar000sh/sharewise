package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns a waived debt into a call on the ledger service (R17, design D7). Asynchronous,
 * after the commit.
 */
@Component
class DebtWaivedListener {

	private final GroupLedgerService ledgers;

	DebtWaivedListener(GroupLedgerService ledgers) {
		this.ledgers = ledgers;
	}

	@ApplicationModuleListener
	void on(DebtWaived event) {
		ledgers.bookDebtWaiver(event);
	}

}
