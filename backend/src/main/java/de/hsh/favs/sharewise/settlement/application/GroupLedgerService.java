package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupLedgerService {

	private final GroupLedgerRepository ledgers;
	private final Clock clock;

	/** Uses the application's {@code Clock} bean when there is one (tests replace it), else the system clock. */
	@Autowired
	GroupLedgerService(GroupLedgerRepository ledgers, ObjectProvider<Clock> clock) {
		this(ledgers, clock.getIfAvailable(Clock::systemUTC));
	}

	GroupLedgerService(GroupLedgerRepository ledgers, Clock clock) {
		this.ledgers = ledgers;
		this.clock = clock;
	}

	@Transactional
	public void apply(ExpenseRecorded expense) {
		var group = expense.groupId().value();
		ledgers.createIfAbsent(group);
		var ledger = ledgers.findForUpdate(group).orElseThrow();
		if (ledger.appendExpenseEntry(expense, clock.instant())) {
			ledgers.save(ledger);
		}
	}

}
