package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplyExpenseToBalances {

	private final GroupLedgerRepository ledgers;

	ApplyExpenseToBalances(GroupLedgerRepository ledgers) {
		this.ledgers = ledgers;
	}

	@Transactional
	public void apply(ExpenseRecorded expense) {
		var group = expense.groupId().value();
		ledgers.createIfAbsent(group);
		var ledger = ledgers.findForUpdate(group).orElseThrow();
		if (ledger.apply(expense)) {
			ledgers.save(ledger);
		}
	}

}
