package de.hsh.favs.sharewise.notification.persistence;

import de.hsh.favs.sharewise.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Stores the inbox entries and answers the queries of one recipient's inbox.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	/**
	 * Stores a new entry unless one with the same recipient, type, reference and time
	 * exists, which is what a redelivered event looks like. Needs a running transaction.
	 * @return true when this call inserted the row
	 */
	default boolean insertIfAbsent(Notification notification) {
		return insertIfAbsent(notification.id().value(), notification.recipient().value(), notification.type().name(),
				notification.groupId().value(), notification.actor().value(), notification.reference(),
				notification.occurredAt()) == 1;
	}

	@Modifying
	@Query(value = """
			INSERT INTO notifications (id, recipient_id, type, group_id, actor_id, reference, occurred_at)
			VALUES (:id, :recipient, :type, :groupId, :actor, :reference, :occurredAt)
			ON CONFLICT DO NOTHING""", nativeQuery = true)
	int insertIfAbsent(@Param("id") UUID id, @Param("recipient") UUID recipient, @Param("type") String type,
			@Param("groupId") UUID groupId, @Param("actor") UUID actor, @Param("reference") UUID reference,
			@Param("occurredAt") Instant occurredAt);

	/** The recipient's entries, newest first; only the unread ones when asked. */
	@Query("""
			select n from Notification n
			where n.recipientId = :recipient and (:unreadOnly = false or n.readAt is null)
			order by n.occurredAt desc, n.id desc""")
	List<Notification> findInbox(@Param("recipient") UUID recipient, @Param("unreadOnly") boolean unreadOnly,
			Pageable page);

	long countByRecipientIdAndReadAtIsNull(UUID recipientId);

	/** An entry, but only when it belongs to the recipient. */
	Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

	@Modifying(clearAutomatically = true)
	@Query("update Notification n set n.readAt = :now where n.recipientId = :recipient and n.readAt is null")
	int markAllRead(@Param("recipient") UUID recipient, @Param("now") Instant now);

}
