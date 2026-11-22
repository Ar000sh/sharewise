package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import de.hsh.favs.sharewise.expense.api.ExpenseHistory;
import de.hsh.favs.sharewise.group.api.GroupFinancialHistory;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.persistence.*;
import de.hsh.favs.sharewise.shared.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class GroupFinancialHistoryTest {

	@Test
	void refusesAnAcceptedExpenseThatHasNotReachedTheLedger() {
		var expenses = mock(ExpenseHistory.class);
		var payments = mock(SettlementPaymentRepository.class);
		var ledgers = mock(GroupLedgerRepository.class);
		var groups = mock(GroupMembership.class);
		var group = new GroupId(UUID.randomUUID());
		var expense = UUID.randomUUID();
		var person = UUID.randomUUID();
		when(ledgers.findForUpdate(group.value())).thenReturn(Optional.of(new GroupLedger(group)));
		when(expenses.of(group)).thenReturn(List.of(new GroupFinancialHistory.Expense(expense, "Receipt", "10.00",
				LocalDate.now(), person, person, "ACTIVE", List.of(), Instant.EPOCH, null, null)));
		var service = new GroupFinancialHistoryImpl(expenses, payments, ledgers, groups);
		assertThatThrownBy(() -> service.capture(group)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.FINANCIAL_PROCESSING_PENDING));
		verify(ledgers, never()).balances(group);
	}

}
