package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.domain.EqualSplit;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.expense.persistence.ExpenseRepository;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
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
	 * Records an expense split equally. The order of the checks: the caller is a member
	 * of the group (GROUP_NOT_FOUND, NOT_A_MEMBER), the split is supported
	 * (SPLIT_NOT_SUPPORTED), then the rules of the aggregate.
	 * @throws DomainException with one of the codes above, or those of
	 * {@link Expense#record}
	 */
	@Transactional
	public Expense recordExpense(RecordExpenseCommand command) {
		groups.requireMember(command.groupId(), command.caller());
		if (command.splitStrategy() != SplitStrategy.EQUAL) {
			throw new DomainException(ErrorCode.SPLIT_NOT_SUPPORTED, "Only the EQUAL split is supported");
		}
		var view = groups.view(command.groupId());
		var shares = EqualSplit.split(command.totalAmount(), command.participants());
		var recorded = Expense.record(view, command.title(), command.paidBy(), command.caller(), command.totalAmount(),
				command.date(), shares, command.splitStrategy(), clock.instant());
		expenses.save(recorded.expense());
		events.publishEvent(recorded.event());
		return recorded.expense();
	}

	/** The group's expenses, newest first: by date, then by creation time. */
	@Transactional(readOnly = true)
	public List<Expense> listExpenses(GroupId groupId, UserId caller) {
		groups.requireMember(groupId, caller);
		return expenses.findByGroupIdOrderByDateDescCreatedAtDesc(groupId.value());
	}

}
