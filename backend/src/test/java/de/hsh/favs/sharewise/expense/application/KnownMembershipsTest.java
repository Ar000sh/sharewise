package de.hsh.favs.sharewise.expense.application;

import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.expense.domain.ExpenseFixture;
import de.hsh.favs.sharewise.expense.persistence.MembershipCopyRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The bounded wait for a membership event that has not been applied yet. */
@ExtendWith(MockitoExtension.class)
class KnownMembershipsTest {

	@Mock
	MembershipCopyRepository copy;

	KnownMemberships memberships;

	@BeforeEach
	void setUp() {
		memberships = new KnownMemberships(copy, 4, Duration.ZERO);
	}

	@Test
	void everyoneAlreadyKnownIsAnsweredWithoutWaiting() {
		// given
		var friends = new ExpenseFixture().groupView();
		when(copy.find(GROUP)).thenReturn(Optional.of(friends));

		// when
		var view = memberships.awaiting(GROUP, List.of(ALICE, BOB));

		// then
		assertThat(view).contains(friends);
		verify(copy, times(1)).find(GROUP);
	}

	@Test
	void aMemberWhoseEventIsStillOnItsWayIsWaitedFor() {
		// given bob's MemberJoined arrives between the second and the third look
		var withoutBob = new ExpenseFixture().withGroupMembers(ALICE).groupView();
		var withBob = new ExpenseFixture().withGroupMembers(ALICE, BOB).groupView();
		when(copy.find(GROUP)).thenReturn(Optional.of(withoutBob), Optional.of(withoutBob), Optional.of(withBob));

		// when
		var view = memberships.awaiting(GROUP, List.of(ALICE, BOB));

		// then
		assertThat(view).contains(withBob);
		verify(copy, times(3)).find(GROUP);
	}

	@Test
	void aPersonWhoIsNoMemberIsLookedForOnlyAsOftenAsTheBudgetAllowsThenTheLastViewIsReturned() {
		// given
		var withoutBob = new ExpenseFixture().withGroupMembers(ALICE).groupView();
		when(copy.find(GROUP)).thenReturn(Optional.of(withoutBob));

		// when
		var view = memberships.awaiting(GROUP, List.of(BOB));

		// then
		assertThat(view).contains(withoutBob);
		verify(copy, times(4)).find(GROUP);
	}

	@Test
	void aGroupNoEventHasAnnouncedStaysEmptyAfterTheBudget() {
		// given
		when(copy.find(GROUP)).thenReturn(Optional.empty());

		// when
		var view = memberships.awaiting(GROUP, List.of(ALICE));

		// then
		assertThat(view).isEmpty();
		verify(copy, times(4)).find(GROUP);
	}

}
