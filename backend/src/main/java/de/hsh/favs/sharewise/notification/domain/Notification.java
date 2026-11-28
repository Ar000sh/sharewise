package de.hsh.favs.sharewise.notification.domain;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One thing that happened that a person should hear about, kept in their inbox. Aggregate
 * root of the notification context. It stores ids only, never names or texts: those are
 * looked up when the inbox is read, so names can change and texts can be reworded. It is
 * immutable except for {@code readAt}.
 */
@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	private UUID id;

	@Column(name = "recipient_id", nullable = false)
	private UUID recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	/** Null when nobody did it: a group archived automatically (design 2026-11-28 G5). */
	@Column(name = "actor_id")
	private UUID actorId;

	/**
	 * What the entry points at: the invitation, the person who joined, or the settlement
	 * payment.
	 */
	@Column(nullable = false)
	private UUID reference;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "read_at")
	private Instant readAt;

	protected Notification() {
	}

	/** A new, unread entry for {@code recipient} about something {@code actor} did. */
	public static Notification notify(UserId recipient, NotificationType type, GroupId groupId, UserId actor,
			UUID reference, Instant occurredAt) {
		Notification notification = withoutActor(recipient, type, groupId, reference, occurredAt);
		notification.actorId = actor.value();
		return notification;
	}

	/**
	 * A new, unread entry about something nobody did: a group archived automatically
	 * because its last balance reached 0 (L5).
	 */
	public static Notification withoutActor(UserId recipient, NotificationType type, GroupId groupId, UUID reference,
			Instant occurredAt) {
		Notification notification = new Notification();
		notification.id = UUID.randomUUID();
		notification.recipientId = recipient.value();
		notification.type = type;
		notification.groupId = groupId.value();
		notification.reference = reference;
		notification.occurredAt = occurredAt;
		return notification;
	}

	/** Marks the entry read. Reading twice keeps the first time. */
	public void markRead(Instant now) {
		if (readAt == null) {
			readAt = now;
		}
	}

	public boolean isRead() {
		return readAt != null;
	}

	public NotificationId id() {
		return new NotificationId(id);
	}

	public UserId recipient() {
		return new UserId(recipientId);
	}

	public NotificationType type() {
		return type;
	}

	public GroupId groupId() {
		return new GroupId(groupId);
	}

	/**
	 * Who did it: the person who joined or declined; empty for a group archived
	 * automatically.
	 */
	public Optional<UserId> actor() {
		return Optional.ofNullable(actorId).map(UserId::new);
	}

	public UUID reference() {
		return reference;
	}

	public Instant occurredAt() {
		return occurredAt;
	}

	public Instant readAt() {
		return readAt;
	}

}
