package de.hsh.favs.sharewise.settlement.application;

import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.ALICE;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.BOB;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.CAROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded.Share;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.ModuleIntegrationTest;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Called like another module would: from outside a transaction, against the real
 * database.
 */
@ModuleIntegrationTest
class BalancesImplTest {

	@Autowired
	Balances balances;

	@Autowired
	GroupLedgerService ledgerService;

	@Autowired
	GroupMembership membership;

	@Autowired
	UserProfiles users;

	private GroupId group;

	@BeforeEach
	void groupWithThreeMembers() {
		group = new GroupId(UUID.randomUUID());
		// deliberately not in name order
		when(membership.members(group)).thenReturn(List.of(CAROL, ALICE, BOB));
		nameUser(ALICE, "Alice");
		nameUser(BOB, "Bob");
		nameUser(CAROL, "Carol");
	}

	@Test
	void listsEveryMemberWithZeroWhenNoExpenses() {
		// when
		var result = balances.of(group);

		// then
		assertThat(result).extracting(MemberBalance::displayName).containsExactly("Alice", "Bob", "Carol");
		assertThat(result).extracting(MemberBalance::balanceCents).containsOnly(0L);
	}

	@Test
	void mergesStoredBalancesWithMemberNames() {
		// given
		ledgerService.apply(new ExpenseRecordedFixture().withGroup(group).build());

		// when
		var result = balances.of(group);

		// then
		assertThat(result).containsExactly(new MemberBalance(ALICE, "Alice", 9000),
				new MemberBalance(BOB, "Bob", -4500), new MemberBalance(CAROL, "Carol", -4500));
	}

	@Test
	void sumOfReturnedBalancesIsZero() {
		// given
		ledgerService.apply(new ExpenseRecordedFixture().withGroup(group).build());
		ledgerService.apply(new ExpenseRecordedFixture().withGroup(group)
			.withPayer(BOB)
			.withTotal(3001)
			.withShares(new Share(ALICE, 1001), new Share(BOB, 1000), new Share(CAROL, 1000))
			.build());

		// when
		var result = balances.of(group);

		// then
		assertThat(result.stream().mapToLong(MemberBalance::balanceCents).sum()).isZero();
	}

	@Test
	void aRepeatedExpenseCountsOnce() {
		// given
		var expense = new ExpenseRecordedFixture().withGroup(group).build();
		ledgerService.apply(expense);
		ledgerService.apply(expense);

		// when
		var result = balances.of(group);

		// then
		assertThat(result).extracting(MemberBalance::balanceCents).containsExactly(9000L, -4500L, -4500L);
	}

	private void nameUser(UserId member, String name) {
		var id = new UserId(member.value());
		when(users.find(id)).thenReturn(Optional.of(new UserProfile(id, name, true)));
	}

}
