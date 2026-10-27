package de.hsh.favs.sharewise.expense.application;

import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.CAROL;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.ExpenseFixture;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.expense.persistence.ExpenseRepository;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Orchestration only: what is asked, saved and published, and that a refused call leaves
 * nothing behind.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

	@Mock
	GroupMembership groups;

	@Mock
	ExpenseRepository expenses;

	@Mock
	ApplicationEventPublisher events;

	ExpenseService service;

	@BeforeEach
	void setUp() {
		service = new ExpenseService(groups, expenses, events, Clock.fixed(MutableClock.START, ZoneOffset.UTC));
	}

	private void groupIsOpenWithTheFourFriends() {
		when(groups.view(GROUP)).thenReturn(new ExpenseFixture().groupView());
	}

	@Test
	void recordingChecksTheCallerThenAsksForTheGroupSavesAndPublishesExpenseRecorded() {
		// given
		groupIsOpenWithTheFourFriends();

		// when
		Expense expense = service.recordExpense(new RecordExpenseCommandFixture().build());

		// then
		var order = inOrder(groups, expenses, events);
		order.verify(groups).requireMember(GROUP, ALICE);
		order.verify(groups).view(GROUP);
		order.verify(expenses).save(expense);
		ArgumentCaptor<ExpenseRecorded> published = ArgumentCaptor.forClass(ExpenseRecorded.class);
		order.verify(events).publishEvent(published.capture());
		assertThat(published.getValue().paidBy()).isEqualTo(ALICE);
		assertThat(published.getValue().totalAmount()).isEqualTo(Money.eur("900.00"));
		assertThat(published.getValue().shares()).extracting(ExpenseShare::owedShare).containsOnly(Money.eur("225.00"));
	}

	@Test
	void aCallerWhoIsNotInTheGroupIsRefusedBeforeAnythingElse() {
		// given
		doThrow(new DomainException(ErrorCode.NOT_A_MEMBER, "no")).when(groups).requireMember(GROUP, CAROL);

		// when / then
		assertThatThrownBy(() -> service.recordExpense(new RecordExpenseCommandFixture().withCaller(CAROL).build()))
			.isInstanceOf(DomainException.class);
		verify(groups, never()).view(any());
		verifyNoInteractions(expenses, events);
	}

	@ParameterizedTest
	@EnumSource(value = SplitStrategy.class, names = { "EXACT", "PERCENTAGE" })
	void aSplitStrategyOtherThanEqualIsRefusedAndNothingIsSaved(SplitStrategy strategy) {
		assertThatThrownBy(
				() -> service.recordExpense(new RecordExpenseCommandFixture().withSplitStrategy(strategy).build()))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.SPLIT_NOT_SUPPORTED));
		verifyNoInteractions(expenses, events);
	}

	@Test
	void aRuleOfTheAggregateRefusesTheExpenseAndNothingIsSavedOrPublished() {
		// given a group where carol is no member
		when(groups.view(GROUP)).thenReturn(new ExpenseFixture().withGroupMembers(ALICE, BOB).groupView());

		// when / then
		assertThatThrownBy(
				() -> service.recordExpense(new RecordExpenseCommandFixture().withParticipants(ALICE, CAROL).build()))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.PARTICIPANT_NOT_IN_GROUP));
		verifyNoInteractions(expenses, events);
	}

	@Test
	void anExpenseForAClosedGroupIsRefused() {
		// given
		when(groups.view(GROUP)).thenReturn(new ExpenseFixture().withClosedGroup().groupView());

		// when / then
		assertThatThrownBy(() -> service.recordExpense(new RecordExpenseCommandFixture().build()))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_OPEN));
		verifyNoInteractions(expenses, events);
	}

	@Test
	void listingChecksMembershipFirstThenAsksTheRepositoryNewestFirst() {
		// given
		Expense hotel = new ExpenseFixture().build();
		when(expenses.findByGroupIdOrderByDateDescCreatedAtDesc(GROUP.value())).thenReturn(List.of(hotel));

		// when
		var listed = service.listExpenses(GROUP, BOB);

		// then
		var order = inOrder(groups, expenses);
		order.verify(groups).requireMember(GROUP, BOB);
		order.verify(expenses).findByGroupIdOrderByDateDescCreatedAtDesc(GROUP.value());
		assertThat(listed).containsExactly(hotel);
	}

}
