package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the ledger aggregate: book what the other contexts announce. */
@Service
public class GroupLedgerService {

	private final GroupLedgerRepository ledgers;

	private final Clock clock;

	GroupLedgerService(GroupLedgerRepository ledgers, Clock clock) {
		this.ledgers = ledgers;
		this.clock = clock;
	}

	/**
	 * Books an expense in its group's ledger, creating the ledger on the first event.
	 * Applying the same event twice books it once: the event may be delivered more than
	 * once.
	 */
	@Transactional
	public void bookExpense(ExpenseRecorded expense) {
		var group = expense.groupId().value();
		ledgers.createIfAbsent(group);
		GroupLedger ledger = ledgers.findForUpdate(group).orElseThrow();
		if (!ledgers.isBooked(group, expense.expenseId().value())) {
			ledger.appendExpenseEntry(expense, clock.instant());
			ledgers.save(ledger);
		}
	}

}
