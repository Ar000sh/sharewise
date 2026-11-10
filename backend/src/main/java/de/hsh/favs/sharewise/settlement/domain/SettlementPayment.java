package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentId;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRecorded;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRejected;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * A payment from a debtor to a creditor inside a group, or a debt the creditor waives. A
 * payment is reported by the debtor (PENDING) and answered once by the creditor
 * (CONFIRMED or REJECTED); a waiver is WAIVED from the start. It never changes the ledger
 * itself: the ledger reacts to the events (R16, R17). {@code @Version} makes two
 * concurrent answers to the same payment fail instead of both succeeding.
 */
@Entity
@Table(name = "settlement_payments")
public class SettlementPayment {

	private static final int MAX_NOTE_LENGTH = 500;

	@Id
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(name = "from_debtor", nullable = false)
	private UUID fromDebtor;

	@Column(name = "to_creditor", nullable = false)
	private UUID toCreditor;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	/** The debtor's remark, later replaced by the rejection or waiver reason. */
	@Column(length = MAX_NOTE_LENGTH)
	private String note;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	/**
	 * Boxed on purpose: the id is assigned here, so Spring Data can only tell a new
	 * payment by a null version; it then persists instead of merging (an extra select and
	 * a copy). Hibernate sets 0 on insert.
	 */
	@Version
	@Column(nullable = false)
	private Long version;

	protected SettlementPayment() {
	}

	private SettlementPayment(GroupId groupId, UserId fromDebtor, UserId toCreditor, Money amount, PaymentStatus status,
			String note, Instant now) {
		this.id = UUID.randomUUID();
		this.groupId = groupId.value();
		this.fromDebtor = fromDebtor.value();
		this.toCreditor = toCreditor.value();
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.status = status;
		this.note = note;
		this.createdAt = now;
	}

	/** Result of {@link #record}: the new payment and the event to publish. */
	public record Recorded(SettlementPayment payment, SettlementPaymentRecorded event) {
	}

	/**
	 * Result of {@link #waive}: the new, already finished payment and the event to
	 * publish.
	 */
	public record Waived(SettlementPayment payment, DebtWaived event) {
	}

	/**
	 * The debtor reports a payment to the creditor. It is PENDING and changes no balance
	 * until the creditor confirms (R16). The amount may not exceed what the debtor still
	 * owes the creditor ({@code openDebt}, already reduced by other pending payments).
	 * The note is optional.
	 * @return the new payment and {@link SettlementPaymentRecorded}
	 * @throws DomainException with {@code BAD_REQUEST} when debtor and creditor are the
	 * same person, the currencies differ or the note is longer than 500 characters
	 * @throws DomainException with {@code AMOUNT_NOT_POSITIVE} when the amount is zero or
	 * negative
	 * @throws DomainException with {@code PAYMENT_EXCEEDS_OPEN_DEBT} when the amount is
	 * above {@code openDebt}
	 */
	public static Recorded record(GroupId groupId, UserId fromDebtor, UserId toCreditor, Money amount, Money openDebt,
			String note, Instant now) {
		requireValid(fromDebtor, toCreditor, amount, openDebt);
		if (note != null) {
			requireNoteLength(note);
		}
		SettlementPayment payment = new SettlementPayment(groupId, fromDebtor, toCreditor, amount,
				PaymentStatus.PENDING, note, now);
		return new Recorded(payment,
				new SettlementPaymentRecorded(payment.paymentId(), groupId, fromDebtor, toCreditor, amount, now));
	}

	/**
	 * The creditor confirms that the money arrived; the debt shrinks by the amount (R16).
	 * @return {@link SettlementPaymentConfirmed}
	 * @throws DomainException with {@code NOT_THE_CREDITOR} when the caller is not the
	 * creditor
	 * @throws DomainException with {@code PAYMENT_NOT_PENDING} when the payment was
	 * already answered
	 */
	public SettlementPaymentConfirmed confirm(UserId caller, Instant now) {
		requireCreditorAndPending(caller);
		status = PaymentStatus.CONFIRMED;
		resolvedAt = now;
		return new SettlementPaymentConfirmed(paymentId(), groupId(), fromDebtor(), toCreditor(), amount(), now);
	}

	/**
	 * The creditor says the money did not arrive; the debt stays as it was (R16). The
	 * reason replaces the note.
	 * @return {@link SettlementPaymentRejected}
	 * @throws DomainException with {@code NOT_THE_CREDITOR} when the caller is not the
	 * creditor
	 * @throws DomainException with {@code PAYMENT_NOT_PENDING} when the payment was
	 * already answered
	 * @throws DomainException with {@code BAD_REQUEST} when the note is blank or longer
	 * than 500 characters
	 */
	public SettlementPaymentRejected reject(UserId caller, String note, Instant now) {
		requireCreditorAndPending(caller);
		requireReason(note);
		status = PaymentStatus.REJECTED;
		this.note = note;
		resolvedAt = now;
		return new SettlementPaymentRejected(paymentId(), groupId(), fromDebtor(), toCreditor(), amount(), now);
	}

	/**
	 * The creditor forgives part of a debt. It needs no confirmation and takes effect at
	 * once (R17, design decision D8). The amount may not exceed {@code openDebt}; the
	 * reason is optional.
	 * @return the new payment (WAIVED) and {@link DebtWaived}
	 * @throws DomainException with {@code BAD_REQUEST} when creditor and debtor are the
	 * same person, the currencies differ or the note is longer than 500 characters
	 * @throws DomainException with {@code AMOUNT_NOT_POSITIVE} when the amount is zero or
	 * negative
	 * @throws DomainException with {@code PAYMENT_EXCEEDS_OPEN_DEBT} when the amount is
	 * above {@code openDebt}
	 */
	public static Waived waive(GroupId groupId, UserId creditor, UserId fromDebtor, Money amount, Money openDebt,
			String note, Instant now) {
		requireValid(fromDebtor, creditor, amount, openDebt);
		if (note != null) {
			requireNoteLength(note);
		}
		SettlementPayment payment = new SettlementPayment(groupId, fromDebtor, creditor, amount, PaymentStatus.WAIVED,
				note, now);
		payment.resolvedAt = now;
		return new Waived(payment, new DebtWaived(payment.paymentId(), groupId, fromDebtor, creditor, amount, now));
	}

	private static void requireValid(UserId fromDebtor, UserId toCreditor, Money amount, Money openDebt) {
		if (fromDebtor.equals(toCreditor)) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "A person cannot pay or forgive themselves");
		}
		if (!amount.currency().equals(openDebt.currency())) {
			throw new DomainException(ErrorCode.BAD_REQUEST,
					"The amount must be in " + openDebt.currency() + ", the currency of the debt");
		}
		if (!amount.isPositive()) {
			throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE, "The amount must be greater than zero");
		}
		if (amount.minus(openDebt).isPositive()) {
			throw new DomainException(ErrorCode.PAYMENT_EXCEEDS_OPEN_DEBT,
					"The amount is larger than the open debt of " + openDebt.amount() + " " + openDebt.currency());
		}
	}

	private static void requireReason(String note) {
		if (note == null || note.isBlank()) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "A reason is required");
		}
		requireNoteLength(note);
	}

	private static void requireNoteLength(String note) {
		if (note.length() > MAX_NOTE_LENGTH) {
			throw new DomainException(ErrorCode.BAD_REQUEST,
					"The note may have at most " + MAX_NOTE_LENGTH + " characters");
		}
	}

	private void requireCreditorAndPending(UserId caller) {
		if (!toCreditor.equals(caller.value())) {
			throw new DomainException(ErrorCode.NOT_THE_CREDITOR, "Only the person who is owed can answer a payment");
		}
		if (status != PaymentStatus.PENDING) {
			throw new DomainException(ErrorCode.PAYMENT_NOT_PENDING, "This payment was already answered");
		}
	}

	public SettlementPaymentId paymentId() {
		return new SettlementPaymentId(id);
	}

	public GroupId groupId() {
		return new GroupId(groupId);
	}

	public UserId fromDebtor() {
		return new UserId(fromDebtor);
	}

	public UserId toCreditor() {
		return new UserId(toCreditor);
	}

	public Money amount() {
		return new Money(amount, Currency.getInstance(currency));
	}

	public PaymentStatus status() {
		return status;
	}

	/** The debtor's note, the rejection reason or the waiver reason; may be null. */
	public String note() {
		return note;
	}

	public Instant createdAt() {
		return createdAt;
	}

	/** When the payment was answered; null while PENDING. */
	public Instant resolvedAt() {
		return resolvedAt;
	}

}
