package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentId;
import de.hsh.favs.sharewise.settlement.domain.PaymentStatus;
import de.hsh.favs.sharewise.settlement.domain.SettlementPayment;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the settlement payment aggregate (R16, R17). Recording and waiving hold
 * the group's ledger lock while the open debt is computed and the payment is saved (D6);
 * answers are protected by the payment's version column. The ledger books the results
 * from the published events, not here (D7).
 */
@Service
public class SettlementPaymentService {

	private final GroupMembership groups;

	private final SettlementPaymentRepository payments;

	private final GroupLedgerRepository ledgers;

	private final OpenDebtPolicy openDebtPolicy;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	SettlementPaymentService(GroupMembership groups, SettlementPaymentRepository payments,
			GroupLedgerRepository ledgers, OpenDebtPolicy openDebtPolicy, ApplicationEventPublisher events,
			Clock clock) {
		this.groups = groups;
		this.payments = payments;
		this.ledgers = ledgers;
		this.openDebtPolicy = openDebtPolicy;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * The caller (the debtor) reports a payment to a creditor; it stays PENDING until the
	 * creditor answers (R16). The order of the checks: the group exists and is not
	 * archived (GROUP_NOT_FOUND, GROUP_NOT_OPEN), the caller is a member (NOT_A_MEMBER),
	 * the creditor is a member (PARTICIPANT_NOT_IN_GROUP), then, under the ledger lock,
	 * the open debt and the rules of {@link SettlementPayment#record}.
	 * @throws DomainException with one of the codes above, or those of
	 * {@link SettlementPayment#record} (PAYMENT_EXCEEDS_OPEN_DEBT, AMOUNT_NOT_POSITIVE,
	 * BAD_REQUEST)
	 */
	@Transactional
	public SettlementPayment record(RecordSettlementPaymentCommand command) {
		GroupId group = command.groupId();
		requireGroupAcceptsSettlementBetween(group, command.caller(), command.toCreditor());
		Money openDebt = lockAndComputeOpenDebt(group, command.caller(), command.toCreditor());
		var recorded = SettlementPayment.record(group, command.caller(), command.toCreditor(), command.amount(),
				openDebt, command.note(), clock.instant());
		payments.save(recorded.payment());
		events.publishEvent(recorded.event());
		return recorded.payment();
	}

	/**
	 * The caller (the creditor) forgives part of a debtor's debt; it takes effect at once
	 * (R17, D8). Same checks and lock as {@link #record}, with the debtor as the other
	 * party.
	 * @throws DomainException with GROUP_NOT_FOUND, NOT_A_MEMBER, GROUP_NOT_OPEN,
	 * PARTICIPANT_NOT_IN_GROUP, or those of {@link SettlementPayment#waive}
	 * (PAYMENT_EXCEEDS_OPEN_DEBT, AMOUNT_NOT_POSITIVE, BAD_REQUEST)
	 */
	@Transactional
	public SettlementPayment waive(WaiveDebtCommand command) {
		GroupId group = command.groupId();
		requireGroupAcceptsSettlementBetween(group, command.caller(), command.fromDebtor());
		Money openDebt = lockAndComputeOpenDebt(group, command.fromDebtor(), command.caller());
		var waived = SettlementPayment.waive(group, command.caller(), command.fromDebtor(), command.amount(), openDebt,
				command.note(), clock.instant());
		payments.save(waived.payment());
		events.publishEvent(waived.event());
		return waived.payment();
	}

	/**
	 * The creditor confirms that the money arrived (R16).
	 * @throws DomainException with SETTLEMENT_PAYMENT_NOT_FOUND when the payment is
	 * unknown or the caller is not a member of its group, NOT_THE_CREDITOR, or
	 * PAYMENT_NOT_PENDING when it was already answered, also by a concurrent answer
	 */
	@Transactional
	public SettlementPayment confirm(UserId caller, SettlementPaymentId paymentId) {
		SettlementPayment payment = loadForAnswer(caller, paymentId);
		var event = payment.confirm(caller, clock.instant());
		saveAnswer(payment);
		events.publishEvent(event);
		return payment;
	}

	/**
	 * The creditor says the money did not arrive; the reservation is released (R16, D4).
	 * @throws DomainException with SETTLEMENT_PAYMENT_NOT_FOUND, NOT_THE_CREDITOR,
	 * PAYMENT_NOT_PENDING (as {@link #confirm}), or BAD_REQUEST when the reason is blank
	 * or too long
	 */
	@Transactional
	public SettlementPayment reject(UserId caller, SettlementPaymentId paymentId, String note) {
		SettlementPayment payment = loadForAnswer(caller, paymentId);
		var event = payment.reject(caller, note, clock.instant());
		saveAnswer(payment);
		events.publishEvent(event);
		return payment;
	}

	/**
	 * The group's settlement payments, newest first.
	 * @throws DomainException with GROUP_NOT_FOUND or NOT_A_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<SettlementPayment> listPayments(GroupId groupId, UserId caller) {
		groups.requireMember(groupId, caller);
		return payments.findByGroupIdOrderByCreatedAtDesc(groupId.value());
	}

	/**
	 * The PENDING payments the caller still has to answer as creditor (D10, I1), newest
	 * first. No archive or former-member filter: no operation produces those states yet
	 * (A4, parked until {@code archiveGroup} or {@code leaveGroup} is built).
	 */
	@Transactional(readOnly = true)
	public List<SettlementPayment> listAwaitingMyAnswer(UserId caller) {
		return payments.findByToCreditorAndStatusOrderByCreatedAtDesc(caller.value(), PaymentStatus.PENDING);
	}

	/**
	 * The checks before a payment or waiver between two people, in this order: the group
	 * exists, it is not archived, the caller is a member, the other person is a member.
	 * @throws DomainException with GROUP_NOT_FOUND, GROUP_NOT_OPEN, NOT_A_MEMBER or
	 * PARTICIPANT_NOT_IN_GROUP
	 */
	private void requireGroupAcceptsSettlementBetween(GroupId group, UserId caller, UserId otherParty) {
		if (!groups.exists(group)) {
			throw new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found");
		}
		if (groups.status(group) == GroupStatus.ARCHIVED) {
			throw new DomainException(ErrorCode.GROUP_NOT_OPEN, "The group is archived");
		}
		if (!groups.isMember(group, caller)) {
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group");
		}
		if (!groups.isMember(group, otherParty)) {
			throw new DomainException(ErrorCode.PARTICIPANT_NOT_IN_GROUP, "The other person is not in this group");
		}
	}

	/**
	 * Lock first, then read (D6): two records or waivers of the same group wait for each
	 * other, so the second sees the reservation of the first. A group without a ledger
	 * gets an empty one, whose open debt is zero.
	 */
	private Money lockAndComputeOpenDebt(GroupId group, UserId debtor, UserId creditor) {
		ledgers.createIfAbsent(group.value());
		ledgers.findForUpdate(group.value()).orElseThrow();
		return openDebtPolicy.openDebt(group, debtor, creditor);
	}

	private SettlementPayment loadForAnswer(UserId caller, SettlementPaymentId paymentId) {
		// a payment of a group the caller is not in is reported as unknown, so the ids
		// of other groups reveal nothing
		return payments.findById(paymentId.value())
			.filter(found -> groups.isMember(found.groupId(), caller))
			.orElseThrow(
					() -> new DomainException(ErrorCode.SETTLEMENT_PAYMENT_NOT_FOUND, "Settlement payment not found"));
	}

	/**
	 * Flushed here, not at commit, so that losing the version check to a concurrent
	 * answer becomes the same PAYMENT_NOT_PENDING a late answer gets.
	 */
	private void saveAnswer(SettlementPayment payment) {
		try {
			payments.saveAndFlush(payment);
		}
		catch (OptimisticLockingFailureException concurrentAnswer) {
			throw new DomainException(ErrorCode.PAYMENT_NOT_PENDING, "This payment was already answered");
		}
	}

}
