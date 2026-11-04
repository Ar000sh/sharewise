package de.hsh.favs.sharewise.notification.application;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.group.api.GroupDirectory;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.notification.domain.Notification;
import de.hsh.favs.sharewise.notification.domain.NotificationId;
import de.hsh.favs.sharewise.notification.domain.NotificationType;
import de.hsh.favs.sharewise.notification.persistence.NotificationRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the inbox: write an entry for an event, read the caller's entries, mark
 * them read. Names are looked up when the inbox is read, through the ports of the account
 * and group contexts.
 */
@Service
public class NotificationService {

	/**
	 * One entry as it is read: the actor and the group name are null when they cannot be
	 * resolved.
	 */
	public record NotificationView(NotificationId id, NotificationType type, Instant occurredAt, Instant readAt,
			UserProfile actor, GroupId groupId, String groupName, UUID reference) {

	}

	/** The entries and how many unread ones the person has in all. */
	public record NotificationList(long unreadCount, List<NotificationView> items) {

	}

	private final NotificationRepository notifications;

	private final UserProfiles profiles;

	private final GroupDirectory groups;

	private final Clock clock;

	NotificationService(NotificationRepository notifications, UserProfiles profiles, GroupDirectory groups,
			Clock clock) {
		this.notifications = notifications;
		this.profiles = profiles;
		this.groups = groups;
		this.clock = clock;
	}

	/**
	 * Stores the entry unless the same event was recorded before (idempotent).
	 * @return true when a new entry was written
	 */
	@Transactional
	public boolean record(Notification notification) {
		return notifications.insertIfAbsent(notification);
	}

	/**
	 * Tells the inviter that somebody joined through their invitation. The creator's own
	 * join has no inviter.
	 */
	@Transactional
	public void recordMemberJoined(MemberJoined event) {
		if (event.invitedBy() == null) {
			return;
		}
		record(Notification.notify(event.invitedBy(), NotificationType.MEMBER_JOINED, event.groupId(), event.userId(),
				event.userId().value(), event.occurredAt()));
	}

	/** Tells the inviter that the invitation was turned down. */
	@Transactional
	public void recordInvitationDeclined(InvitationDeclined event) {
		record(Notification.notify(event.invitedBy(), NotificationType.INVITATION_DECLINED, event.groupId(),
				event.invitee(), event.invitationId().value(), event.occurredAt()));
	}

	/**
	 * The caller's entries, newest first, together with how many unread ones the caller
	 * has in all.
	 * @param limit how many entries to return; the range 1 to 50 is checked by the
	 * controller, which owns the shape of the request
	 */
	@Transactional(readOnly = true)
	public NotificationList list(UserId caller, boolean unreadOnly, int limit) {
		List<Notification> entries = notifications.findInbox(caller.value(), unreadOnly, PageRequest.of(0, limit));
		Map<UserId, UserProfile> actors = profiles
			.findProfiles(entries.stream().map(Notification::actor).collect(Collectors.toSet()))
			.stream()
			.collect(Collectors.toMap(UserProfile::userId, Function.identity(), (a, b) -> a));
		Set<GroupId> groupIds = entries.stream().map(Notification::groupId).collect(Collectors.toSet());
		Map<GroupId, String> names = groups.findNames(groupIds);
		List<NotificationView> items = entries.stream()
			.map(n -> new NotificationView(n.id(), n.type(), n.occurredAt(), n.readAt(), actors.get(n.actor()),
					n.groupId(), names.get(n.groupId()), n.reference()))
			.toList();
		return new NotificationList(notifications.countByRecipientIdAndReadAtIsNull(caller.value()), items);
	}

	/**
	 * Marks one of the caller's entries read.
	 * @throws DomainException NOTIFICATION_NOT_FOUND for an unknown entry or one of
	 * another person
	 */
	@Transactional
	public void markRead(NotificationId id, UserId caller) {
		Notification notification = notifications.findByIdAndRecipientId(id.value(), caller.value())
			.orElseThrow(() -> new DomainException(ErrorCode.NOTIFICATION_NOT_FOUND, "Notification not found"));
		notification.markRead(clock.instant());
	}

	/** Marks all of the caller's unread entries read. */
	@Transactional
	public void markAllRead(UserId caller) {
		notifications.markAllRead(caller.value(), clock.instant());
	}

}
