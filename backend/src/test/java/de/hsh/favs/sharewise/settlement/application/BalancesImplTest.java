package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * The port the other modules call, against a real database and real groups, called with
 * no surrounding transaction.
 */
@ModuleIntegrationTest
class BalancesImplTest {

	@Autowired
	Balances balances;

	@Autowired
	GroupLedgerService ledgers;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void everyCurrentMemberIsListedAndThoseWithoutExpensesHaveZero() throws Exception {
		// given a group of three in which only alice and bob appear in an expense
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		AccountFixture carol = AccountFixture.signUp(mvc, json, "Carol");
		GroupId group = new GroupId(new GroupRequests(mvc, json).groupWith(alice, "Trip", bob, carol));
		ledgers.bookExpense(new ExpenseRecordedFixture().inGroup(group)
			.paidBy(alice.userId())
			.withTotalAmount("100.00")
			.splitBetween(alice.userId(), bob.userId())
			.build());

		// when
		var result = balances.of(group);

		// then
		assertThat(result).containsExactlyInAnyOrder(new MemberBalance(alice.userId(), Money.eur("50.00")),
				new MemberBalance(bob.userId(), Money.eur("-50.00")),
				new MemberBalance(carol.userId(), Money.eur("0.00")));
	}

	@Test
	void aGroupWithoutExpensesListsEveryoneAtZero() throws Exception {
		// given
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		GroupId group = new GroupId(new GroupRequests(mvc, json).createGroup(alice, "Empty"));

		// then
		assertThat(balances.of(group)).containsExactly(new MemberBalance(alice.userId(), Money.eur("0.00")));
	}

	@Test
	void anUnknownGroupIsGroupNotFound() {
		assertThatThrownBy(() -> balances.of(new GroupId(UUID.randomUUID()))).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_FOUND));
	}

}
