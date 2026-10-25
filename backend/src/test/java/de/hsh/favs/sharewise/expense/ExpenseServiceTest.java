package de.hsh.favs.sharewise.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.TestcontainersConfiguration;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.application.RecordExpenseCommand;
import de.hsh.favs.sharewise.expense.application.RecordExpenseUseCase;
import de.hsh.favs.sharewise.expense.application.SplitType;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class ExpenseServiceTest {

	@Autowired RecordExpenseUseCase useCase;
	@Autowired FakeGroupMembership groups;
	@Autowired FakeCurrentUser currentUser;
	@Autowired ApplicationEvents events;

	GroupId group = new GroupId(UUID.randomUUID());
	MemberId alice = new MemberId(FakeCurrentUser.ALICE.id().value());
	MemberId bob = new MemberId(UUID.randomUUID());
	MemberId carol = new MemberId(UUID.randomUUID());
	MemberId outsider = new MemberId(UUID.randomUUID());

	@BeforeEach
	void setUp() {
		groups.reset();
		currentUser.reset();
		groups.group(group, GroupStatus.OPEN, alice, bob, carol);
	}

	RecordExpenseCommand command(MemberId caller, MemberId payer, List<MemberId> participants) {
		return new RecordExpenseCommand(group, caller, "Dinner", 1_000, LocalDate.of(2027, 6, 2), payer,
				participants, SplitType.EQUAL);
	}

	void assertRejected(RecordExpenseCommand command, ErrorCode code) {
		assertThatThrownBy(() -> useCase.record(command)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(code));
		assertThat(events.stream(ExpenseRecorded.class)).isEmpty();
	}

	@Test
	void recordsExpenseAndPublishesExpenseRecordedWithShares() {
		Expense expense = useCase.record(command(alice, bob, List.of(alice, bob, carol)));

		var published = events.stream(ExpenseRecorded.class).toList();
		assertThat(published).hasSize(1);
		var event = published.get(0);
		assertThat(event.expenseId()).isEqualTo(expense.id());
		assertThat(event.groupId()).isEqualTo(group);
		assertThat(event.payerId()).isEqualTo(bob);
		assertThat(event.totalCents()).isEqualTo(1_000);
		assertThat(event.shares()).containsExactly(new ExpenseRecorded.Share(alice, 334),
				new ExpenseRecorded.Share(bob, 333), new ExpenseRecorded.Share(carol, 333));
	}

	@Test
	void callerNotMemberIs403() {
		assertRejected(command(outsider, alice, List.of(alice)), ErrorCode.NOT_A_MEMBER);
	}

	@Test
	void unknownGroupIs404() {
		groups.reset();
		assertRejected(command(alice, alice, List.of(alice)), ErrorCode.GROUP_NOT_FOUND);
	}

	@Test
	void payerNotInGroupIs422() {
		assertRejected(command(alice, outsider, List.of(alice)), ErrorCode.PAYER_NOT_IN_GROUP);
	}

	@Test
	void participantNotInGroupIs422() {
		assertRejected(command(alice, alice, List.of(alice, outsider)), ErrorCode.PARTICIPANT_NOT_IN_GROUP);
	}

	@Test
	void groupNotOpenIs422GROUP_NOT_OPEN() {
		groups.group(group, GroupStatus.CLOSED_FOR_SETTLEMENT, alice, bob, carol);
		assertRejected(command(alice, alice, List.of(alice)), ErrorCode.GROUP_NOT_OPEN);
	}

	@Test
	void groupNotOpenIsCheckedBeforePayer() {
		groups.group(group, GroupStatus.ARCHIVED, alice, bob, carol);
		assertRejected(command(alice, outsider, List.of(alice)), ErrorCode.GROUP_NOT_OPEN);
	}

	@Test
	void nonEqualSplitIs422SplitNotSupported() {
		var percentage = new RecordExpenseCommand(group, alice, "x", 1_000, LocalDate.of(2027, 6, 2), alice,
				List.of(alice), SplitType.PERCENTAGE);
		assertRejected(percentage, ErrorCode.SPLIT_NOT_SUPPORTED);
	}

	@Test
	void domainRulesApplyAfterMembershipChecks() {
		var zero = new RecordExpenseCommand(group, alice, "x", 0, LocalDate.of(2027, 6, 2), alice, List.of(alice),
				SplitType.EQUAL);
		assertRejected(zero, ErrorCode.AMOUNT_NOT_POSITIVE);
		assertRejected(command(alice, alice, List.of()), ErrorCode.NO_PARTICIPANTS);
		assertRejected(command(alice, alice, List.of(alice, alice)), ErrorCode.NO_PARTICIPANTS);
	}

}
