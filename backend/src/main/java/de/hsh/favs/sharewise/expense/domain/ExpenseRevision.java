package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One correction of an {@link Expense}: the version it replaced, who corrected it, when
 * and why (R7, the team diagram's ExpenseRevision). Part of the Expense aggregate, only
 * created through {@link Expense#correct}; never changed afterwards.
 */
@Entity
@Table(name = "expense_revisions")
public class ExpenseRevision {

	@Id
	private UUID id;

	/** 1 for the first correction of an expense; keeps the order when times are equal. */
	@Column(name = "revision_no", nullable = false)
	private int revisionNo;

	@Column(name = "previous_title", nullable = false)
	private String previousTitle;

	@Column(name = "previous_total_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal previousTotalAmount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "previous_date", nullable = false)
	private LocalDate previousDate;

	@Column(name = "previous_paid_by", nullable = false)
	private UUID previousPaidBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_split_strategy", nullable = false)
	private SplitStrategy previousSplitStrategy;

	// Always loaded with the expense, like its shares: callers read it after the
	// transaction.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "expense_revision_shares", joinColumns = @JoinColumn(name = "revision_id"))
	@OrderColumn(name = "position")
	private List<StoredShare> previousShares;

	@Column(name = "corrected_by", nullable = false)
	private UUID correctedBy;

	@Column(name = "corrected_at", nullable = false)
	private Instant correctedAt;

	@Column(length = 200)
	private String reason;

	protected ExpenseRevision() {
	}

	ExpenseRevision(int revisionNo, ExpenseTitle title, Money totalAmount, LocalDate date, UserId paidBy,
			SplitStrategy splitStrategy, List<ExpenseShare> shares, UserId correctedBy, Instant correctedAt,
			String reason) {
		this.id = UUID.randomUUID();
		this.revisionNo = revisionNo;
		this.previousTitle = title.value();
		this.previousTotalAmount = totalAmount.amount();
		this.currency = totalAmount.currency().getCurrencyCode();
		this.previousDate = date;
		this.previousPaidBy = paidBy.value();
		this.previousSplitStrategy = splitStrategy;
		this.previousShares = shares.stream().map(StoredShare::new).toList();
		this.correctedBy = correctedBy.value();
		this.correctedAt = correctedAt;
		this.reason = reason;
	}

	public UUID id() {
		return id;
	}

	public ExpenseTitle previousTitle() {
		return new ExpenseTitle(previousTitle);
	}

	public Money previousTotalAmount() {
		return new Money(previousTotalAmount, currency());
	}

	public LocalDate previousDate() {
		return previousDate;
	}

	public UserId previousPaidBy() {
		return new UserId(previousPaidBy);
	}

	public SplitStrategy previousSplitStrategy() {
		return previousSplitStrategy;
	}

	public List<ExpenseShare> previousShares() {
		Currency currency = currency();
		return previousShares.stream().map(share -> share.toShare(currency)).toList();
	}

	public UserId correctedBy() {
		return new UserId(correctedBy);
	}

	public Instant correctedAt() {
		return correctedAt;
	}

	/** Why it was corrected; empty when nobody said. */
	public Optional<String> reason() {
		return Optional.ofNullable(reason);
	}

	private Currency currency() {
		return Currency.getInstance(currency);
	}

}
