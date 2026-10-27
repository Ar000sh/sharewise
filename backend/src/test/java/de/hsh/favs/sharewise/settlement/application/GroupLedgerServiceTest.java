package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Orchestration only: the ledger is created if absent, locked, and an expense is booked
 * once.
 */
@ExtendWith(MockitoExtension.class)
class GroupLedgerServiceTest {

	@Mock
	GroupLedgerRepository ledgers;

	GroupLedgerService service;

	ExpenseRecorded event = new ExpenseRecordedFixture().build();

	GroupLedger ledger = new GroupLedger(event.groupId());

	@BeforeEach
	void setUp() {
		service = new GroupLedgerService(ledgers, Clock.fixed(MutableClock.START, ZoneOffset.UTC));
		when(ledgers.findForUpdate(event.groupId().value())).thenReturn(Optional.of(ledger));
	}

	@Test
	void anExpenseIsBookedAfterCreatingAndLockingTheLedger() {
		// given
		when(ledgers.isBooked(event.groupId().value(), event.expenseId().value())).thenReturn(false);

		// when
		service.bookExpense(event);

		// then
		var order = inOrder(ledgers);
		order.verify(ledgers).createIfAbsent(event.groupId().value());
		order.verify(ledgers).findForUpdate(event.groupId().value());
		order.verify(ledgers).isBooked(event.groupId().value(), event.expenseId().value());
		order.verify(ledgers).save(ledger);
		assertThat(ledger.entries()).hasSize(5);
		assertThat(ledger.entries()).allSatisfy(e -> assertThat(e.occurredAt()).isEqualTo(MutableClock.START));
	}

	@Test
	void anExpenseThatIsAlreadyBookedIsNotBookedAgain() {
		// given the event is delivered a second time
		when(ledgers.isBooked(event.groupId().value(), event.expenseId().value())).thenReturn(true);

		// when
		service.bookExpense(event);

		// then
		verify(ledgers, never()).save(any());
		assertThat(ledger.entries()).isEmpty();
	}

}
