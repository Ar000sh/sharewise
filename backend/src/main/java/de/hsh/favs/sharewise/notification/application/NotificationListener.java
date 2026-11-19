package de.hsh.favs.sharewise.notification.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.MemberPromoted;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRejected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The events of the group, expense and settlement contexts that create an inbox entry
 * (I2), each handed to the notification service. Every method is asynchronous, runs after
 * the commit in its own transaction, has its own entry in the event publication registry,
 * takes everything from the event and uses no request state.
 */
@Component
class NotificationListener {

	private final NotificationService notifications;

	NotificationListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(MemberJoined event) {
		notifications.recordMemberJoined(event);
	}

	@ApplicationModuleListener
	void on(ExpenseRecorded event) {
		notifications.recordExpenseRecorded(event);
	}

	@ApplicationModuleListener
	void on(InvitationDeclined event) {
		notifications.recordInvitationDeclined(event);
	}

	@ApplicationModuleListener
	void on(MemberPromoted event) {
		notifications.recordMemberPromoted(event);
	}

	@ApplicationModuleListener
	void on(SettlementPaymentConfirmed event) {
		notifications.recordPaymentConfirmed(event);
	}

	@ApplicationModuleListener
	void on(SettlementPaymentRejected event) {
		notifications.recordPaymentRejected(event);
	}

	@ApplicationModuleListener
	void on(DebtWaived event) {
		notifications.recordDebtWaived(event);
	}

}
