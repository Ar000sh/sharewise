package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** A recorded expense with its equal shares. Aggregate root; immutable once created. */
@Entity
@Table(name = "expense")
public class Expense {

	@Id
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(nullable = false)
	private String title;

	@Column(name = "amount_cents", nullable = false)
	private long amountCents;

	@Column(name = "expense_date", nullable = false)
	private LocalDate date;

	@Column(name = "payer_id", nullable = false)
	private UUID payerId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	// Always loaded with the expense: callers read them after the service transaction has
	// ended.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "expense_share", joinColumns = @JoinColumn(name = "expense_id"))
	@OrderColumn(name = "position")
	private List<Share> shares;

	protected Expense() {
	}

	private Expense(GroupId groupId, String title, Money amount, LocalDate date, MemberId payer, List<Share> shares) {
		this.id = UUID.randomUUID();
		this.groupId = groupId.value();
		this.title = title;
		this.amountCents = amount.cents();
		this.date = date;
		this.payerId = payer.value();
		this.shares = shares;
		this.createdAt = Instant.now();
	}

	public static Expense record(GroupId groupId, String title, Money amount, LocalDate date, MemberId payer,
			List<MemberId> participants) {
		if (!amount.isPositive()) {
			throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE, "Amount must be greater than zero");
		}
		if (participants.isEmpty()) {
			throw new DomainException(ErrorCode.NO_PARTICIPANTS, "At least one participant is required");
		}
		if (new HashSet<>(participants).size() != participants.size()) {
			throw new DomainException(ErrorCode.NO_PARTICIPANTS, "Participants must be distinct");
		}
		return new Expense(groupId, title, amount, date, payer, EqualSplit.split(amount, participants));
	}

	public UUID id() {
		return id;
	}

	public GroupId groupId() {
		return new GroupId(groupId);
	}

	public String title() {
		return title;
	}

	public Money amount() {
		return Money.eur(amountCents);
	}

	public LocalDate date() {
		return date;
	}

	public MemberId payerId() {
		return new MemberId(payerId);
	}

	public List<Share> shares() {
		return List.copyOf(shares);
	}

}
