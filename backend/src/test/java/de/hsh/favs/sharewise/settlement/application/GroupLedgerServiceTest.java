package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupLedgerServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

	@Mock
	GroupLedgerRepository ledgers;

	GroupLedgerService service;

	@BeforeEach
	void service() {
		service = new GroupLedgerService(ledgers, Clock.fixed(NOW, ZoneOffset.UTC));
	}

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
		assertThat(ledger.entries()).isNotEmpty().allSatisfy(entry -> assertThat(entry.occurredAt()).isEqualTo(NOW));
	}

	@Test
	void aRepeatedExpenseSavesNothing() {
		// given
		var group = new GroupId(UUID.randomUUID());
		var ledger = new GroupLedger(group);
		var expense = new ExpenseRecordedFixture().withGroup(group).build();
		when(ledgers.findForUpdate(group.value())).thenReturn(Optional.of(ledger));
		when(ledgers.isBooked(group.value(), expense.expenseId())).thenReturn(true);

		// when
		service.apply(expense);

		// then
		verify(ledgers, never()).save(ledger);
		assertThat(ledger.entries()).isEmpty();
	}

}
