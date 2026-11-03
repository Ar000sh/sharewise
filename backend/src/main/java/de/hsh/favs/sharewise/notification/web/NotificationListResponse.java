package de.hsh.favs.sharewise.notification.web;

import de.hsh.favs.sharewise.notification.application.NotificationService.NotificationList;
import java.util.List;

/** Response of {@code GET /notifications}. */
record NotificationListResponse(long unreadCount, List<NotificationResponse> items) {

	static NotificationListResponse of(NotificationList list) {
		return new NotificationListResponse(list.unreadCount(),
				list.items().stream().map(NotificationResponse::of).toList());
	}

}
