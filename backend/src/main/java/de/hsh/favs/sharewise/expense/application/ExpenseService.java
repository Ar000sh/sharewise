package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.persistence.ExpenseRepository;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and lists the expenses of a group. Group data (membership, status) comes from
 * the group module's API.
 */
@Service
public class ExpenseService {

	private final GroupMembership groups;

	private final ExpenseRepository repository;

	private final ApplicationEventPublisher events;

	ExpenseService(GroupMembership groups, ExpenseRepository repository, ApplicationEventPublisher events) {
		this.groups = groups;
		this.repository = repository;
		this.events = events;
	}

	@Transactional
	public Expense record(RecordExpenseCommand command) {
		var group = command.groupId();
		groups.requireMember(group, command.caller());
		if (groups.status(group) != GroupStatus.OPEN) {
			throw new DomainException(ErrorCode.GROUP_NOT_OPEN, "The group is not open for new expenses");
		}
		if (!groups.isMember(group, command.payerId())) {
			throw new DomainException(ErrorCode.PAYER_NOT_IN_GROUP, "The payer is not a member of the group");
		}
		for (var participant : command.participantIds()) {
			if (!groups.isMember(group, participant)) {
				throw new DomainException(ErrorCode.PARTICIPANT_NOT_IN_GROUP,
						"A participant is not a member of the group");
			}
		}
		if (command.split() != SplitType.EQUAL) {
			throw new DomainException(ErrorCode.SPLIT_NOT_SUPPORTED, "Only the EQUAL split is supported");
		}

		var expense = Expense.record(group, command.title(), Money.eur(command.amountCents()), command.date(),
				command.payerId(), command.participantIds());
		repository.save(expense);
		events.publishEvent(new ExpenseRecorded(expense.id(), group, expense.payerId(), expense.amount().cents(),
				expense.shares()
					.stream()
					.map(s -> new ExpenseRecorded.Share(s.memberId(), s.amount().cents()))
					.toList()));
		return expense;
	}

	/** Newest first: by date, then creation time. */
	@Transactional(readOnly = true)
	public List<Expense> list(GroupId groupId, MemberId caller) {
		groups.requireMember(groupId, caller);
		return repository.findByGroupIdOrderByDateDescCreatedAtDesc(groupId.value());
	}

}
