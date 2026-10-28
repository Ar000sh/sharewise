package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.domain.EqualSplit;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.GroupMembershipView;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.expense.persistence.ExpenseRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Use cases of the expense aggregate. Facts about the group (who is in it, is it open)
 * come from the expense context's own copy, fed by the group context's events, so the
 * context does not call the group module at all.
 */
@Service
public class ExpenseService {

	private final KnownMemberships memberships;

	private final ExpenseRepository expenses;

	private final ApplicationEventPublisher events;

	private final TransactionOperations transactions;

	private final Clock clock;

	ExpenseService(KnownMemberships memberships, ExpenseRepository expenses, ApplicationEventPublisher events,
			TransactionOperations transactions, Clock clock) {
		this.memberships = memberships;
		this.expenses = expenses;
		this.events = events;
		this.transactions = transactions;
		this.clock = clock;
	}

	/**
	 * Records an expense split equally. The order of the checks: the caller is a member
	 * of the group (GROUP_NOT_FOUND, NOT_A_MEMBER), the split is supported
	 * (SPLIT_NOT_SUPPORTED), then the rules of the aggregate. The wait for a membership
	 * that has not arrived yet happens before the transaction, so no connection is held
	 * while sleeping.
	 * @throws DomainException with one of the codes above, or those of
	 * {@link Expense#record}
	 */
	public Expense recordExpense(RecordExpenseCommand command) {
		var needed = new ArrayList<UserId>(command.participants());
		needed.add(command.paidBy());
		needed.add(command.caller());
		var view = requireKnownMember(command.groupId(), command.caller(), needed);
		if (command.splitStrategy() != SplitStrategy.EQUAL) {
			throw new DomainException(ErrorCode.SPLIT_NOT_SUPPORTED, "Only the EQUAL split is supported");
		}
		var shares = EqualSplit.split(command.totalAmount(), command.participants());
		var recorded = Expense.record(view, command.title(), command.paidBy(), command.caller(), command.totalAmount(),
				command.date(), shares, command.splitStrategy(), clock.instant());
		return transactions.execute(status -> {
			expenses.save(recorded.expense());
			events.publishEvent(recorded.event());
			return recorded.expense();
		});
	}

	/** The group's expenses, newest first: by date, then by creation time. */
	public List<Expense> listExpenses(GroupId groupId, UserId caller) {
		requireKnownMember(groupId, caller, List.of(caller));
		return expenses.findByGroupIdOrderByDateDescCreatedAtDesc(groupId.value());
	}

	private GroupMembershipView requireKnownMember(GroupId group, UserId caller, List<UserId> needed) {
		var view = memberships.awaiting(group, needed)
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
		if (!view.isActiveMember(caller)) {
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group");
		}
		return view;
	}

}
