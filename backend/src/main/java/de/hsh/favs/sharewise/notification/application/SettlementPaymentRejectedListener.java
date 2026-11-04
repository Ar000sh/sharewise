package de.hsh.favs.sharewise.notification.application;

import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRejected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the settlement context's event into an inbox entry for the debtor (I2).
 * Asynchronous, after the commit; it takes everything from the event and uses no request
 * state.
 */
@Component
class SettlementPaymentRejectedListener {

	private final NotificationService notifications;

	SettlementPaymentRejectedListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(SettlementPaymentRejected event) {
		notifications.recordPaymentRejected(event);
	}

}
