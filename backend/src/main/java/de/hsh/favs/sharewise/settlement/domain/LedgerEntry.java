package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * One booking in a group's ledger. Entries are never changed; a correction adds reversing
 * entries. Part of the {@link GroupLedger} aggregate: only the root creates them, there
 * is no repository for them.
 */
@Entity
@Table(name = "settlement_ledger_entries")
public class LedgerEntry {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private GroupLedger ledger;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/**
	 * Positive for a credit (the person is owed), negative for a debit (the person owes).
	 */
	@Column(name = "delta_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal deltaAmount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false)
	private LedgerEntryType type;

	/** The expense (later also the payment or waiver) this entry comes from. */
	@Column(name = "reference_id", nullable = false)
	private UUID referenceId;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected LedgerEntry() {
	}

	LedgerEntry(GroupLedger ledger, UserId userId, Money deltaAmount, LedgerEntryType type, UUID referenceId,
			Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.ledger = ledger;
		this.userId = userId.value();
		this.deltaAmount = deltaAmount.amount();
		this.currency = deltaAmount.currency().getCurrencyCode();
		this.type = type;
		this.referenceId = referenceId;
		this.occurredAt = occurredAt;
	}

	public EntryId entryId() {
		return new EntryId(id);
	}

	public UserId userId() {
		return new UserId(userId);
	}

	public Money deltaAmount() {
		return new Money(deltaAmount, Currency.getInstance(currency));
	}

	public LedgerEntryType type() {
		return type;
	}

	public UUID referenceId() {
		return referenceId;
	}

	public Instant occurredAt() {
		return occurredAt;
	}

}
