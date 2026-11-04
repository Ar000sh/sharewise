package de.hsh.favs.sharewise.notification.web;

import de.hsh.favs.sharewise.notification.application.NotificationService.NotificationView;
import de.hsh.favs.sharewise.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * One inbox entry. {@code actor} and {@code group} are null when they cannot be resolved.
 */
record NotificationResponse(UUID id, NotificationType type, Instant occurredAt, Instant readAt,
		UserSummaryResponse actor, GroupRefResponse group, UUID reference) {

	static NotificationResponse of(NotificationView view) {
		return new NotificationResponse(view.id().value(), view.type(), view.occurredAt(), view.readAt(),
				view.actor() == null ? null : UserSummaryResponse.of(view.actor()),
				view.groupName() == null ? null : new GroupRefResponse(view.groupId().value(), view.groupName()),
				view.reference());
	}

}
