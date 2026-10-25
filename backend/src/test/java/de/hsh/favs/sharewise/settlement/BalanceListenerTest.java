package de.hsh.favs.sharewise.settlement;

import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.ALICE;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.BOB;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.CAROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.TestcontainersConfiguration;
import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserDirectory;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The event flow: a published ExpenseRecorded changes the balances, asynchronously after commit. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class BalanceListenerTest {

	@MockitoBean
	GroupMembership membership;

	@MockitoBean
	UserDirectory users;

	@MockitoBean
	CurrentUser currentUser;

	@Autowired
	Balances balances;

	@Autowired
	ApplyExpenseToBalances applyExpense;

	@Test
	void publishedExpenseUpdatesBalances(Scenario scenario) {
		// given
		var group = new GroupId(UUID.randomUUID());
		when(membership.members(group)).thenReturn(List.of(ALICE, BOB, CAROL));
		var event = new ExpenseRecordedFixture().withGroup(group).build();

		// when / then
		scenario.publish(event)
				.andWaitForStateChange(() -> balances.of(group), BalanceListenerTest::hasNonZeroBalance)
				.andVerify(list -> assertThat(list).extracting(MemberBalance::balanceCents)
						.containsExactlyInAnyOrder(9000L, -4500L, -4500L));
	}

	@Test
	void redeliveredExpenseIsNotCountedTwice() {
		// given
		var group = new GroupId(UUID.randomUUID());
		when(membership.members(group)).thenReturn(List.of(ALICE, BOB, CAROL));
		var event = new ExpenseRecordedFixture().withGroup(group).build();

		// when
		applyExpense.apply(event);
		applyExpense.apply(event);

		// then
		assertThat(balances.of(group)).extracting(MemberBalance::balanceCents)
				.containsExactlyInAnyOrder(9000L, -4500L, -4500L);
	}

	private static boolean hasNonZeroBalance(List<MemberBalance> list) {
		return list.stream().anyMatch(b -> b.balanceCents() != 0);
	}

}
