package de.hsh.favs.sharewise.notification.application;

import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the settlement context's event into an inbox entry for the debtor (I2).
 * Asynchronous, after the commit; it takes everything from the event and uses no request
 * state. The bean is named explicitly because the settlement module has a listener of the
 * same class name (the one that books the payment).
 */
@Component("notificationSettlementPaymentConfirmedListener")
class SettlementPaymentConfirmedListener {

	private final NotificationService notifications;

	SettlementPaymentConfirmedListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(SettlementPaymentConfirmed event) {
		notifications.recordPaymentConfirmed(event);
	}

}
