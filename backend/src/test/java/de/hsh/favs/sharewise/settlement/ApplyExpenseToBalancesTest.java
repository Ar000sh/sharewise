package de.hsh.favs.sharewise.settlement;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.shared.GroupId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplyExpenseToBalancesTest {

	@Mock
	GroupLedgerRepository ledgers;

	@InjectMocks
	ApplyExpenseToBalances service;

	@Test
	void createsTheLedgerBeforeLockingAndSavesIt() {
		// given
		var group = new GroupId(UUID.randomUUID());
		var ledger = new GroupLedger(group);
		when(ledgers.findForUpdate(group.value())).thenReturn(Optional.of(ledger));

		// when
		service.apply(new ExpenseRecordedFixture().withGroup(group).build());

		// then
		var order = inOrder(ledgers);
		order.verify(ledgers).createIfAbsent(group.value());
		order.verify(ledgers).findForUpdate(group.value());
		order.verify(ledgers).save(ledger);
	}

	@Test
	void aRepeatedExpenseSavesNothing() {
		// given
		var group = new GroupId(UUID.randomUUID());
		var ledger = new GroupLedger(group);
		var expense = new ExpenseRecordedFixture().withGroup(group).build();
		ledger.apply(expense);
		when(ledgers.findForUpdate(group.value())).thenReturn(Optional.of(ledger));

		// when
		service.apply(expense);

		// then
		verify(ledgers, never()).save(ledger);
	}

}
