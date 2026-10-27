package de.hsh.favs.sharewise.settlement.domain;

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
import java.time.Instant;
import java.util.UUID;

/**
 * One booking in a group's ledger (domain model: LedgerEntry). Entries are never changed;
 * a correction adds reversing entries. Part of the {@link GroupLedger} aggregate: only
 * the root creates them, there is no repository.
 */
@Entity
@Table(name = "settlement_ledger_entry")
public class LedgerEntry {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private GroupLedger ledger;

	@Column(name = "member_id", nullable = false)
	private UUID memberId;

	/**
	 * Positive for a credit (the member is owed), negative for a debit (the member owes).
	 */
	@Column(name = "delta_cents", nullable = false)
	private long deltaCents;

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

	LedgerEntry(GroupLedger ledger, UserId member, long deltaCents, LedgerEntryType type, UUID referenceId,
			Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.ledger = ledger;
		this.memberId = member.value();
		this.deltaCents = deltaCents;
		this.type = type;
		this.referenceId = referenceId;
		this.occurredAt = occurredAt;
	}

	public UserId member() {
		return new UserId(memberId);
	}

	public long deltaCents() {
		return deltaCents;
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
