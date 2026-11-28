package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.api.ExpenseId;
import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.expense.domain.EqualSplit;
import de.hsh.favs.sharewise.expense.domain.ExactSplit;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.PercentageSplit;
import de.hsh.favs.sharewise.expense.domain.SplitEntry;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.expense.persistence.ExpenseRepository;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the expense aggregate. Facts about the group (who is in it, is it open)
 * come from the group module's {@link GroupMembership} port as a fresh snapshot, so the
 * expense never keeps its own copy.
 */
@Service
public class ExpenseService {

	private final GroupMembership groups;

	private final ExpenseRepository expenses;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ExpenseService(GroupMembership groups, ExpenseRepository expenses, ApplicationEventPublisher events, Clock clock) {
		this.groups = groups;
		this.expenses = expenses;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Records an expense, split by the command's strategy (EQUAL, EXACT or PERCENTAGE).
	 * The order of the checks: the caller is a member of the group (GROUP_NOT_FOUND,
	 * NOT_A_MEMBER), then the split turns the typed values into shares
	 * (PERCENTAGES_NOT_100 and the rules of the split), then the rules of the aggregate.
	 * @throws DomainException with one of the codes above, or those of
	 * {@link Expense#record}
	 */
	@Transactional
	public Expense recordExpense(RecordExpenseCommand command) {
		groups.requireMember(command.groupId(), command.caller());
		var view = groups.view(command.groupId());
		var shares = shares(command.totalAmount(), command.participants(), command.splitStrategy());
		var recorded = Expense.record(view, command.title(), command.paidBy(), command.caller(), command.totalAmount(),
				command.date(), shares, command.splitStrategy(), clock.instant());
		expenses.save(recorded.expense());
		events.publishEvent(recorded.event());
		return recorded.expense();
	}

	/**
	 * Corrects an expense (J5): the same split as recording, then the rules of
	 * {@link Expense#correct}. The expense row is locked, so two corrections at the same
	 * moment become two revisions in order.
	 * @throws DomainException GROUP_NOT_FOUND, NOT_A_MEMBER, EXPENSE_NOT_FOUND, the codes
	 * of the split, or those of {@link Expense#correct}
	 */
	@Transactional
	public Expense correctExpense(CorrectExpenseCommand command) {
		groups.requireMember(command.groupId(), command.caller());
		Expense expense = lockedExpense(command.groupId(), command.expenseId());
		var view = groups.view(command.groupId());
		var shares = shares(command.totalAmount(), command.participants(), command.splitStrategy());
		var corrected = expense.correct(view, command.title(), command.paidBy(), command.totalAmount(), command.date(),
				shares, command.splitStrategy(), command.reason(), command.caller(), clock.instant());
		expenses.save(expense);
		events.publishEvent(corrected);
		return expense;
	}

	/**
	 * Removes an expense (J6, a soft delete). The expense row is locked like for a
	 * correction.
	 * @throws DomainException GROUP_NOT_FOUND, NOT_A_MEMBER, EXPENSE_NOT_FOUND, or those
	 * of {@link Expense#remove}
	 */
	@Transactional
	public void removeExpense(GroupId groupId, ExpenseId expenseId, UserId caller) {
		groups.requireMember(groupId, caller);
		Expense expense = lockedExpense(groupId, expenseId);
		var removed = expense.remove(groups.view(groupId), caller, clock.instant());
		expenses.save(expense);
		events.publishEvent(removed);
	}

	private Expense lockedExpense(GroupId groupId, ExpenseId expenseId) {
		return expenses.findInGroupForUpdate(groupId.value(), expenseId.value())
			.orElseThrow(() -> new DomainException(ErrorCode.EXPENSE_NOT_FOUND, "Expense not found"));
	}

	/** Turns the typed values into shares with the chosen split (R4). */
	private static List<ExpenseShare> shares(Money total, List<SplitEntry> entries, SplitStrategy strategy) {
		return switch (strategy) {
			case EQUAL -> EqualSplit.split(total, entries.stream().map(SplitEntry::participantId).toList());
			case EXACT -> ExactSplit.split(total, entries);
			case PERCENTAGE -> PercentageSplit.split(total, entries);
		};
	}

	/** The group's expenses, newest first: by date, then by creation time. */
	@Transactional(readOnly = true)
	public List<Expense> listExpenses(GroupId groupId, UserId caller) {
		groups.requireMember(groupId, caller);
		return expenses.findByGroupIdOrderByDateDescCreatedAtDesc(groupId.value());
	}

}
