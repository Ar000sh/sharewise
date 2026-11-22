package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.api.ExpenseHistory;
import de.hsh.favs.sharewise.expense.persistence.ExpenseRepository;
import de.hsh.favs.sharewise.group.api.GroupFinancialHistory;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ExpenseHistoryImpl implements ExpenseHistory {

	private final ExpenseRepository expenses;

	ExpenseHistoryImpl(ExpenseRepository expenses) {
		this.expenses = expenses;
	}

	@Override
	@Transactional(readOnly = true)
	public List<GroupFinancialHistory.Expense> of(GroupId group) {
		return expenses.findByGroupIdOrderByDateDescCreatedAtDesc(group.value())
			.stream()
			.map(e -> new GroupFinancialHistory.Expense(e.expenseId().value(), e.title().value(),
					e.totalAmount().amount().toPlainString(), e.date(), e.paidBy().value(), e.recordedBy().value(),
					e.status().name(),
					e.shares()
						.stream()
						.map(s -> new GroupFinancialHistory.Share(s.participantId().value(),
								s.owedShare().amount().toPlainString()))
						.toList(),
					e.createdAt(), e.reversalId(), e.correctedFrom()))
			.toList();
	}

}
