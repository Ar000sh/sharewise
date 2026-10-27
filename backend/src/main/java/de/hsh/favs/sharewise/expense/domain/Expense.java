package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseId;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.group.api.GroupMembershipView;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
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
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Something one member paid for the group, split between participants. Aggregate root of
 * the expense context (core subdomain). The rules about who may pay, who may take part
 * and when an expense may be recorded are checked against a {@link GroupMembershipView},
 * because an expense only knows its group by id.
 */
@Entity
@Table(name = "expenses")
public class Expense {

	/** The new expense together with the event that announces it. */
	public record Recorded(Expense expense, ExpenseRecorded event) {

	}

	@Id
	private UUID id;

	@Column(name = "group_id", nullable = false)
	private UUID groupId;

	@Column(nullable = false)
	private String title;

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal totalAmount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "expense_date", nullable = false)
	private LocalDate date;

	@Column(name = "paid_by", nullable = false)
	private UUID paidBy;

	@Column(name = "recorded_by", nullable = false)
	private UUID recordedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "split_strategy", nullable = false)
	private SplitStrategy splitStrategy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExpenseStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	// Always loaded with the expense: callers read the shares after the service
	// transaction has ended.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "expense_shares", joinColumns = @JoinColumn(name = "expense_id"))
	@OrderColumn(name = "position")
	private List<StoredShare> shares;

	protected Expense() {
	}

	/**
	 * Records an expense. The checks run in this order, each with its own error: the
	 * group is open (GROUP_NOT_OPEN, R5), the payer and the person recording it are
	 * active members (PAYER_NOT_IN_GROUP, NOT_A_MEMBER, R2), there is at least one
	 * participant and none twice (NO_PARTICIPANTS), every participant is an active member
	 * (PARTICIPANT_NOT_IN_GROUP, R3), the total is positive (AMOUNT_NOT_POSITIVE, R1).
	 * @param view who is in the group and whether it is open, at this moment
	 * @param shares what each participant owes; they must add up to the total (R4)
	 * @throws IllegalArgumentException when the shares do not add up to the total, which
	 * means the split was calculated wrongly
	 */
	public static Recorded record(GroupMembershipView view, ExpenseTitle title, UserId paidBy, UserId recordedBy,
			Money totalAmount, LocalDate date, List<ExpenseShare> shares, SplitStrategy splitStrategy, Instant now) {
		if (!view.isOpen()) {
			throw new DomainException(ErrorCode.GROUP_NOT_OPEN, "The group is not open for new expenses");
		}
		if (!view.isActiveMember(paidBy)) {
			throw new DomainException(ErrorCode.PAYER_NOT_IN_GROUP, "The payer is not a member of the group");
		}
		if (!view.isActiveMember(recordedBy)) {
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group");
		}
		if (shares.isEmpty()) {
			throw new DomainException(ErrorCode.NO_PARTICIPANTS, "At least one participant is required");
		}
		var participants = new HashSet<UserId>();
		for (ExpenseShare share : shares) {
			if (!participants.add(share.participantId())) {
				throw new DomainException(ErrorCode.NO_PARTICIPANTS, "Participants must be distinct");
			}
			if (!view.isActiveMember(share.participantId())) {
				throw new DomainException(ErrorCode.PARTICIPANT_NOT_IN_GROUP,
						"A participant is not a member of the group");
			}
		}
		if (!totalAmount.isPositive()) {
			throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE, "Amount must be greater than zero");
		}
		Money sum = shares.stream()
			.map(ExpenseShare::owedShare)
			.reduce(Money.zero(totalAmount.currency()), Money::plus);
		if (!sum.equals(totalAmount)) {
			throw new IllegalArgumentException("The shares add up to " + sum + ", not to " + totalAmount);
		}

		Expense expense = new Expense();
		expense.id = UUID.randomUUID();
		expense.groupId = view.groupId().value();
		expense.title = title.value();
		expense.totalAmount = totalAmount.amount();
		expense.currency = totalAmount.currency().getCurrencyCode();
		expense.date = date;
		expense.paidBy = paidBy.value();
		expense.recordedBy = recordedBy.value();
		expense.splitStrategy = splitStrategy;
		expense.status = ExpenseStatus.ACTIVE;
		expense.createdAt = now;
		expense.shares = shares.stream().map(StoredShare::new).toList();
		return new Recorded(expense,
				new ExpenseRecorded(expense.expenseId(), view.groupId(), paidBy, totalAmount, shares, now));
	}

	public ExpenseId expenseId() {
		return new ExpenseId(id);
	}

	public GroupId groupId() {
		return new GroupId(groupId);
	}

	public ExpenseTitle title() {
		return new ExpenseTitle(title);
	}

	public Money totalAmount() {
		return new Money(totalAmount, currency());
	}

	public LocalDate date() {
		return date;
	}

	public UserId paidBy() {
		return new UserId(paidBy);
	}

	public UserId recordedBy() {
		return new UserId(recordedBy);
	}

	public SplitStrategy splitStrategy() {
		return splitStrategy;
	}

	public ExpenseStatus status() {
		return status;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public List<ExpenseShare> shares() {
		Currency currency = currency();
		return shares.stream().map(share -> share.toShare(currency)).toList();
	}

	private Currency currency() {
		return Currency.getInstance(currency);
	}

}
