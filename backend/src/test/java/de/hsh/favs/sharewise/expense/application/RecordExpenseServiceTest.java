package de.hsh.favs.sharewise.expense.application;

import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.CAROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RecordExpenseServiceTest {

	@Mock GroupMembership groups;
	@Mock ExpenseRepository repository;
	@Mock ApplicationEventPublisher events;

	RecordExpenseService service;

	MemberId outsider = new MemberId(UUID.randomUUID());
	GroupId group = new RecordExpenseCommandFixture().build().groupId();

	@BeforeEach
	void setUp() {
		service = new RecordExpenseService(groups, repository, events);
		lenient().when(groups.status(group)).thenReturn(GroupStatus.OPEN);
		for (MemberId member : new MemberId[] { ALICE, BOB, CAROL }) {
			lenient().when(groups.isMember(group, member)).thenReturn(true);
		}
	}

	void assertRejectedWith(RecordExpenseCommand command, ErrorCode code) {
		assertThatThrownBy(() -> service.record(command)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(code));
		verifyNoInteractions(repository, events);
	}

	@Test
	void savesTheExpenseThenPublishesExpenseRecordedWithTheShares() {
		// given
		var command = new RecordExpenseCommandFixture().withPayer(BOB).withAmountCents(1_000)
				.withParticipants(ALICE, BOB, CAROL).build();

		// when
		Expense expense = service.record(command);

		// then
		InOrder order = inOrder(groups, repository, events);
		order.verify(groups).requireMember(group, ALICE);
		order.verify(repository).save(expense);
		var event = ArgumentCaptor.forClass(ExpenseRecorded.class);
		order.verify(events).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new ExpenseRecorded(expense.id(), group, BOB, 1_000,
				java.util.List.of(new ExpenseRecorded.Share(ALICE, 334), new ExpenseRecorded.Share(BOB, 333),
						new ExpenseRecorded.Share(CAROL, 333))));
	}

	@Test
	void callerWhoIsNotAMemberIsRejectedAndNothingHappens() {
		// given
		doThrow(new DomainException(ErrorCode.NOT_A_MEMBER, "no")).when(groups).requireMember(group, outsider);

		// when / then
		assertRejectedWith(new RecordExpenseCommandFixture().withCaller(outsider).build(), ErrorCode.NOT_A_MEMBER);
	}

	@Test
	void groupThatIsNotOpenIsCheckedBeforePayerAndParticipants() {
		// given
		when(groups.status(group)).thenReturn(GroupStatus.CLOSED_FOR_SETTLEMENT);

		// when / then
		assertRejectedWith(new RecordExpenseCommandFixture().withPayer(outsider).build(), ErrorCode.GROUP_NOT_OPEN);
	}

	@Test
	void payerOutsideTheGroupIsRejected() {
		assertRejectedWith(new RecordExpenseCommandFixture().withPayer(outsider).build(),
				ErrorCode.PAYER_NOT_IN_GROUP);
	}

	@Test
	void participantOutsideTheGroupIsRejected() {
		assertRejectedWith(new RecordExpenseCommandFixture().withParticipants(ALICE, outsider).build(),
				ErrorCode.PARTICIPANT_NOT_IN_GROUP);
	}

	@Test
	void splitOtherThanEqualIsRejected() {
		assertRejectedWith(new RecordExpenseCommandFixture().withSplit(SplitType.PERCENTAGE).build(),
				ErrorCode.SPLIT_NOT_SUPPORTED);
	}

	@Test
	void domainRulesAreAppliedAfterTheMembershipChecks() {
		assertRejectedWith(new RecordExpenseCommandFixture().withAmountCents(0).build(),
				ErrorCode.AMOUNT_NOT_POSITIVE);
		assertRejectedWith(new RecordExpenseCommandFixture().withParticipants(ALICE, ALICE).build(),
				ErrorCode.NO_PARTICIPANTS);
	}

}
