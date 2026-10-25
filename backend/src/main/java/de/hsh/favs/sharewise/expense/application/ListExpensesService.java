package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListExpensesService implements ListExpensesQuery {

	private final GroupMembership groups;
	private final ExpenseRepositoryPort repository;

	ListExpensesService(GroupMembership groups, ExpenseRepositoryPort repository) {
		this.groups = groups;
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Expense> list(GroupId groupId, MemberId caller) {
		groups.requireMember(groupId, caller);
		return repository.findByGroup(groupId);
	}

}
