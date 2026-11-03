package de.hsh.favs.sharewise.notification.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.notification.application.NotificationService;
import de.hsh.favs.sharewise.notification.domain.NotificationId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's inbox: list, mark one read, mark all read. Every call is about the
 * caller's own entries.
 */
@RestController
class NotificationController {

	private final NotificationService notifications;

	private final CurrentUser currentUser;

	NotificationController(NotificationService notifications, CurrentUser currentUser) {
		this.notifications = notifications;
		this.currentUser = currentUser;
	}

	@GetMapping("/notifications")
	NotificationListResponse list(@RequestParam(defaultValue = "false") boolean unread,
			@RequestParam(defaultValue = "10") int limit) {
		return NotificationListResponse.of(notifications.list(caller(), unread, limit));
	}

	@PostMapping("/notifications/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markAllRead() {
		notifications.markAllRead(caller());
	}

	@PostMapping("/notifications/{notificationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@PathVariable UUID notificationId) {
		notifications.markRead(new NotificationId(notificationId), caller());
	}

	private UserId caller() {
		return currentUser.get().userId();
	}

}
