package de.hsh.favs.sharewise.notification.application;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.group.api.GroupArchived;
import de.hsh.favs.sharewise.group.api.GroupClosedForSettlement;
import de.hsh.favs.sharewise.group.api.GroupDirectory;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupReopened;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.MemberPromoted;
import de.hsh.favs.sharewise.group.api.MemberRemoved;
import de.hsh.favs.sharewise.notification.domain.Notification;
import de.hsh.favs.sharewise.notification.domain.NotificationId;
import de.hsh.favs.sharewise.notification.domain.NotificationType;
import de.hsh.favs.sharewise.notification.persistence.NotificationRepository;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRejected;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
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

	private final GroupMembership groupMembers;

	private final Clock clock;

	NotificationService(NotificationRepository notifications, UserProfiles profiles, GroupDirectory groups,
			GroupMembership groupMembers, Clock clock) {
		this.notifications = notifications;
		this.profiles = profiles;
		this.groups = groups;
		this.groupMembers = groupMembers;
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

	/** Tells a member that an admin made them an admin. */
	@Transactional
	public void recordMemberPromoted(MemberPromoted event) {
		record(Notification.notify(event.userId(), NotificationType.MEMBER_PROMOTED, event.groupId(),
				event.promotedBy(), event.userId().value(), event.occurredAt()));
	}

	/** Tells a person that an admin removed them from the group (J11). */
	@Transactional
	public void recordMemberRemoved(MemberRemoved event) {
		record(Notification.notify(event.userId(), NotificationType.MEMBER_REMOVED, event.groupId(), event.removedBy(),
				event.userId().value(), event.occurredAt()));
	}

	/**
	 * Tells every member except the admin that the group was closed for settlement (J12).
	 */
	@Transactional
	public void recordGroupClosed(GroupClosedForSettlement event) {
		for (UserId member : membersExcept(event.groupId(), event.closedBy())) {
			record(Notification.notify(member, NotificationType.GROUP_CLOSED, event.groupId(), event.closedBy(),
					event.groupId().value(), event.occurredAt()));
		}
	}

	/** Tells every member except the admin that the group is open again (J12b). */
	@Transactional
	public void recordGroupReopened(GroupReopened event) {
		for (UserId member : membersExcept(event.groupId(), event.reopenedBy())) {
			record(Notification.notify(member, NotificationType.GROUP_REOPENED, event.groupId(), event.reopenedBy(),
					event.groupId().value(), event.occurredAt()));
		}
	}

	/**
	 * Tells every member except the admin that the group was archived (J13). An automatic
	 * archive has no admin: everybody gets an entry without an actor (design 2026-11-28
	 * G5).
	 */
	@Transactional
	public void recordGroupArchived(GroupArchived event) {
		for (UserId member : membersExcept(event.groupId(), event.archivedBy())) {
			record(event.archivedBy() == null
					? Notification.withoutActor(member, NotificationType.GROUP_ARCHIVED, event.groupId(),
							event.groupId().value(), event.occurredAt())
					: Notification.notify(member, NotificationType.GROUP_ARCHIVED, event.groupId(), event.archivedBy(),
							event.groupId().value(), event.occurredAt()));
		}
	}

	/**
	 * The group's active members at the time the event is handled, without the person who
	 * acted (none when {@code actor} is null) (design 2026-11-28 G6).
	 */
	private List<UserId> membersExcept(GroupId groupId, UserId actor) {
		return groupMembers.members(groupId).stream().filter(member -> !member.equals(actor)).toList();
	}

	/** Tells the inviter that the invitation was turned down. */
	@Transactional
	public void recordInvitationDeclined(InvitationDeclined event) {
		record(Notification.notify(event.invitedBy(), NotificationType.INVITATION_DECLINED, event.groupId(),
				event.invitee(), event.invitationId().value(), event.occurredAt()));
	}

	/** Tells the debtor that the creditor confirmed the payment. */
	@Transactional
	public void recordPaymentConfirmed(SettlementPaymentConfirmed event) {
		record(Notification.notify(event.fromDebtor(), NotificationType.PAYMENT_CONFIRMED, event.groupId(),
				event.toCreditor(), event.paymentId().value(), event.occurredAt()));
	}

	/** Tells the debtor that the creditor rejected the payment. */
	@Transactional
	public void recordPaymentRejected(SettlementPaymentRejected event) {
		record(Notification.notify(event.fromDebtor(), NotificationType.PAYMENT_REJECTED, event.groupId(),
				event.toCreditor(), event.paymentId().value(), event.occurredAt()));
	}

	/** Tells the debtor that the creditor forgave part of the debt. */
	@Transactional
	public void recordDebtWaived(DebtWaived event) {
		record(Notification.notify(event.fromDebtor(), NotificationType.DEBT_WAIVED, event.groupId(),
				event.toCreditor(), event.paymentId().value(), event.occurredAt()));
	}

	/**
	 * Tells everybody involved in an expense that somebody recorded it: the participants
	 * and the payer, but not the recorder, who knows. One entry per person; a repeated
	 * event adds none.
	 */
	@Transactional
	public void recordExpenseRecorded(ExpenseRecorded event) {
		Set<UserId> involved = new LinkedHashSet<>();
		event.shares().forEach(share -> involved.add(share.participantId()));
		involved.add(event.paidBy());
		involved.remove(event.recordedBy());
		for (UserId recipient : involved) {
			record(Notification.notify(recipient, NotificationType.EXPENSE_RECORDED, event.groupId(),
					event.recordedBy(), event.expenseId().value(), event.occurredAt()));
		}
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
			.findProfiles(entries.stream().flatMap(n -> n.actor().stream()).collect(Collectors.toSet()))
			.stream()
			.collect(Collectors.toMap(UserProfile::userId, Function.identity(), (a, b) -> a));
		Set<GroupId> groupIds = entries.stream().map(Notification::groupId).collect(Collectors.toSet());
		Map<GroupId, String> names = groups.findNames(groupIds);
		List<NotificationView> items = entries.stream()
			.map(n -> new NotificationView(n.id(), n.type(), n.occurredAt(), n.readAt(),
					n.actor().map(actors::get).orElse(null), n.groupId(), names.get(n.groupId()), n.reference()))
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
