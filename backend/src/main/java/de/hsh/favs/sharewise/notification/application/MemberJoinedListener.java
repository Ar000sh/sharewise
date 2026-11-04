package de.hsh.favs.sharewise.notification.application;

import de.hsh.favs.sharewise.group.api.MemberJoined;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the group context's event into an inbox entry for the inviter. Asynchronous,
 * after the commit; it takes everything from the event and uses no request state.
 */
@Component
class MemberJoinedListener {

	private final NotificationService notifications;

	MemberJoinedListener(NotificationService notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(MemberJoined event) {
		notifications.recordMemberJoined(event);
	}

}
