package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.ExpenseIntegrationTest;
import de.hsh.favs.sharewise.expense.StubGroupMembership;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.CAROL;
import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.Share;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;

/** Called from outside a transaction, like the controller does: proves the database and event wiring. */
@ExpenseIntegrationTest
class RecordAndListExpensesIntegrationTest {

	@Autowired RecordExpenseUseCase recordExpense;
	@Autowired ListExpensesUseCase listExpenses;
	@Autowired StubGroupMembership groups;
	@Autowired ApplicationEvents events;

	@Test
	void recordedExpenseIsStoredWithSharesInGivenOrderAndPublishedOnce() {
		// given
		var group = new GroupId(UUID.randomUUID());
		groups.group(group, GroupStatus.OPEN, ALICE, BOB, CAROL);
		var command = new RecordExpenseCommandFixture().withGroupId(group).withPayer(CAROL)
				.withParticipants(CAROL, ALICE, BOB).build();

		// when
		Expense recorded = recordExpense.record(command);

		// then
		Expense stored = listExpenses.list(group, ALICE).get(0);
		assertThat(stored.id()).isEqualTo(recorded.id());
		assertThat(stored.payerId()).isEqualTo(CAROL);
		assertThat(stored.shares()).extracting(Share::memberId).containsExactly(CAROL, ALICE, BOB);
		assertThat(stored.shares()).extracting(s -> s.amount().cents()).containsExactly(334L, 333L, 333L);
		assertThat(events.stream(ExpenseRecorded.class).filter(e -> e.expenseId().equals(recorded.id())))
				.hasSize(1);
	}

}
