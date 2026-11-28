package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.expense.api.ExpenseCorrected;
import de.hsh.favs.sharewise.expense.api.ExpenseId;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseRemoved;
import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.group.api.GroupMembershipView;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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

	/** Earlier versions, oldest first (R7); loaded with the expense, like the shares. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "expense_id", nullable = false)
	@OrderBy("revisionNo ASC")
	private List<ExpenseRevision> revisions = new ArrayList<>();

	protected Expense() {
	}

	/**
	 * Records an expense. The checks run in this order, each with its own error: the
	 * group is open (GROUP_NOT_OPEN, R5), the payer and the person recording it are
	 * active members (PAYER_NOT_IN_GROUP, NOT_A_MEMBER, R2), there is at least one
	 * participant and none twice (NO_PARTICIPANTS), every participant is an active member
	 * (PARTICIPANT_NOT_IN_GROUP, R3), the person recording it is one of the participants
	 * (RECORDER_NOT_PARTICIPANT, R18), the total is positive (AMOUNT_NOT_POSITIVE, R1),
	 * the shares add up to the total (SHARES_NOT_TOTAL, R4).
	 * @param view who is in the group and whether it is open, at this moment
	 * @param shares what each participant owes; they must add up to the total (R4)
	 * @throws DomainException with the code of the first rule above that fails
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
		requireValidVersion(view, recordedBy, totalAmount, shares);

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
				new ExpenseRecorded(expense.expenseId(), view.groupId(), paidBy, recordedBy, totalAmount, shares, now));
	}

	/**
	 * Replaces this expense with a corrected version; the old one becomes a revision and
	 * the status CORRECTED (J5, R7). The new version follows the rules of recording, so a
	 * member who joined later may be added (R10). The checks run in this order: the group
	 * is open (GROUP_NOT_OPEN, R5, L3), the expense was not removed (EXPENSE_REMOVED),
	 * the actor is the recorder, the payer or an admin (NOT_ALLOWED_TO_CHANGE_EXPENSE,
	 * R6), nobody in the current version has left the group (FORMER_MEMBER_INVOLVED,
	 * design 2026-11-28 E3), the new payer is an active member (PAYER_NOT_IN_GROUP), then
	 * the rules of the new shares as in {@link #record}.
	 * @param reason why it was corrected; may be null
	 * @return the {@link ExpenseCorrected} event with both versions
	 * @throws DomainException with the code of the first rule above that fails
	 */
	public ExpenseCorrected correct(GroupMembershipView view, ExpenseTitle newTitle, UserId newPaidBy,
			Money newTotalAmount, LocalDate newDate, List<ExpenseShare> newShares, SplitStrategy newSplitStrategy,
			String reason, UserId actor, Instant now) {
		requireChangeableBy(view, actor);
		if (!view.isActiveMember(newPaidBy)) {
			throw new DomainException(ErrorCode.PAYER_NOT_IN_GROUP, "The payer is not a member of the group");
		}
		requireValidVersion(view, recordedBy(), newTotalAmount, newShares);

		ExpenseRevision revision = new ExpenseRevision(revisions.size() + 1, title(), totalAmount(), date, paidBy(),
				splitStrategy, shares(), actor, now, reason);
		ExpenseCorrected event = new ExpenseCorrected(expenseId(), groupId(), revision.id(), actor, paidBy(),
				totalAmount(), shares(), newPaidBy, newTotalAmount, newShares, now);
		revisions.add(revision);
		title = newTitle.value();
		totalAmount = newTotalAmount.amount();
		date = newDate;
		paidBy = newPaidBy.value();
		splitStrategy = newSplitStrategy;
		// a mutable list: Hibernate updates the stored collection of a managed expense in
		// place
		shares = new ArrayList<>(newShares.stream().map(StoredShare::new).toList());
		status = ExpenseStatus.CORRECTED;
		return event;
	}

	/**
	 * Removes this expense: it no longer counts for balances but stays visible (J6, R8, a
	 * soft delete). The same checks as {@link #correct} up to FORMER_MEMBER_INVOLVED.
	 * @return the {@link ExpenseRemoved} event with the version that counted until now
	 * @throws DomainException GROUP_NOT_OPEN, EXPENSE_REMOVED,
	 * NOT_ALLOWED_TO_CHANGE_EXPENSE, FORMER_MEMBER_INVOLVED
	 */
	public ExpenseRemoved remove(GroupMembershipView view, UserId actor, Instant now) {
		requireChangeableBy(view, actor);
		status = ExpenseStatus.REMOVED;
		return new ExpenseRemoved(expenseId(), groupId(), UUID.randomUUID(), actor, paidBy(), totalAmount(), shares(),
				now);
	}

	/** The checks a correction and a removal share (R5, R6, design 2026-11-28 E3, E4). */
	private void requireChangeableBy(GroupMembershipView view, UserId actor) {
		if (!view.isOpen()) {
			throw new DomainException(ErrorCode.GROUP_NOT_OPEN, "Expenses can only be changed while the group is open");
		}
		if (status == ExpenseStatus.REMOVED) {
			throw new DomainException(ErrorCode.EXPENSE_REMOVED, "This expense was removed");
		}
		if (!actor.equals(recordedBy()) && !actor.equals(paidBy()) && !view.isAdmin(actor)) {
			throw new DomainException(ErrorCode.NOT_ALLOWED_TO_CHANGE_EXPENSE,
					"Only the recorder, the payer or an admin can change this expense");
		}
		boolean formerMemberInvolved = !view.isActiveMember(paidBy())
				|| shares().stream().anyMatch(share -> !view.isActiveMember(share.participantId()));
		if (formerMemberInvolved) {
			throw new DomainException(ErrorCode.FORMER_MEMBER_INVOLVED,
					"Somebody in this expense has left the group, so it can no longer change");
		}
	}

	/**
	 * The rules every version of an expense follows, recorded or corrected: at least one
	 * participant and none twice (NO_PARTICIPANTS), every participant an active member
	 * (PARTICIPANT_NOT_IN_GROUP, R3), the recorder one of them (RECORDER_NOT_PARTICIPANT,
	 * R18), the total positive (AMOUNT_NOT_POSITIVE, R1), the shares adding up to it
	 * (SHARES_NOT_TOTAL, R4).
	 */
	private static void requireValidVersion(GroupMembershipView view, UserId recordedBy, Money totalAmount,
			List<ExpenseShare> shares) {
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
		if (!participants.contains(recordedBy)) {
			throw new DomainException(ErrorCode.RECORDER_NOT_PARTICIPANT,
					"The person recording an expense must be one of its participants");
		}
		if (!totalAmount.isPositive()) {
			throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE, "Amount must be greater than zero");
		}
		Money sum = shares.stream()
			.map(ExpenseShare::owedShare)
			.reduce(Money.zero(totalAmount.currency()), Money::plus);
		if (!sum.equals(totalAmount)) {
			throw new DomainException(ErrorCode.SHARES_NOT_TOTAL,
					"The shares add up to " + sum + ", not to " + totalAmount);
		}
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

	/** The earlier versions, oldest first; empty when it was never corrected (R7). */
	public List<ExpenseRevision> revisions() {
		return List.copyOf(revisions);
	}

	private Currency currency() {
		return Currency.getInstance(currency);
	}

}
