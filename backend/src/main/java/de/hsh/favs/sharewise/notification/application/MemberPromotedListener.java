package de.hsh.favs.sharewise.notification.application;

import de.hsh.favs.sharewise.group.api.MemberPromoted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the group context's event into an inbox entry for the promoted member.
 * Asynchronous, after the commit; it takes everything from the event and uses no request
 * state.
 */
@Component
class MemberPromotedListener {

	private final NotificationService notifications;

	MemberPromotedListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(MemberPromoted event) {
		notifications.recordMemberPromoted(event);
	}

}
