package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** R14: the payer is credited the total, every share holder is debited their share. */
@Service
class ApplyExpenseToBalances {

	private final BalanceStore store;

	ApplyExpenseToBalances(BalanceStore store) {
		this.store = store;
	}

	@Transactional
	void apply(ExpenseRecorded expense) {
		if (!store.markProcessed(expense.expenseId())) {
			return;
		}
		Map<MemberId, Long> deltas = new HashMap<>();
		deltas.merge(expense.payerId(), expense.totalCents(), Long::sum);
		for (var share : expense.shares()) {
			deltas.merge(share.memberId(), -share.shareCents(), Long::sum);
		}
		// fixed order, so concurrent transactions touching the same rows cannot deadlock
		deltas.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(m -> m.value())))
				.forEach(delta -> store.add(expense.groupId(), delta.getKey(), delta.getValue()));
	}

}
