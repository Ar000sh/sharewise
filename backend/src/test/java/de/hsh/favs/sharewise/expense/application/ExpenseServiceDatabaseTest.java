package de.hsh.favs.sharewise.expense.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.ExpenseTitle;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a mocked repository cannot prove: shares and exact decimals survive a round trip
 * through Postgres, and the service works when called with no surrounding transaction.
 */
@ModuleIntegrationTest
class ExpenseServiceDatabaseTest {

	@Autowired
	ExpenseService service;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void recordedSharesComeBackInTheGivenOrderWithExactAmounts() throws Exception {
		// given a group of three
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		AccountFixture carol = AccountFixture.signUp(mvc, json, "Carol");
		GroupId group = new GroupId(new GroupRequests(mvc, json).groupWith(alice, "Trip", bob, carol));

		// when 100.00 is recorded with carol first, and read back in a separate call
		service.recordExpense(new RecordExpenseCommand(group, alice.userId(), new ExpenseTitle("Tapas"),
				Money.eur("100.00"), LocalDate.parse("2027-06-01"), alice.userId(),
				List.of(carol.userId(), alice.userId(), bob.userId()), SplitStrategy.EQUAL));
		List<Expense> listed = service.listExpenses(group, bob.userId());

		// then
		assertThat(listed).singleElement().satisfies(expense -> {
			assertThat(expense.totalAmount()).isEqualTo(Money.eur("100.00"));
			assertThat(expense.shares()).extracting(ExpenseShare::participantId)
				.containsExactly(carol.userId(), alice.userId(), bob.userId());
			assertThat(expense.shares()).extracting(ExpenseShare::owedShare)
				.containsExactly(Money.eur("33.34"), Money.eur("33.33"), Money.eur("33.33"));
		});
	}

}
