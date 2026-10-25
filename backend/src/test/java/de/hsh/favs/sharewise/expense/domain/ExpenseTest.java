package de.hsh.favs.sharewise.expense.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseTest {

	GroupId group = new GroupId(UUID.randomUUID());
	MemberId alice = new MemberId(UUID.randomUUID());
	MemberId bob = new MemberId(UUID.randomUUID());
	LocalDate date = LocalDate.of(2027, 6, 1);

	@Test
	void recordsExpenseWithEqualShares() {
		var expense = Expense.record(group, "Hotel", Money.eur(90_000), date, alice, List.of(alice, bob));

		assertThat(expense.id()).isNotNull();
		assertThat(expense.groupId()).isEqualTo(group);
		assertThat(expense.title()).isEqualTo("Hotel");
		assertThat(expense.payerId()).isEqualTo(alice);
		assertThat(expense.shares()).extracting(Share::memberId).containsExactly(alice, bob);
		assertThat(expense.shares()).extracting(s -> s.amount().cents()).containsExactly(45_000L, 45_000L);
	}

	@Test
	void rejectsZeroAndNegativeAmountAMOUNT_NOT_POSITIVE() {
		for (long cents : new long[] { 0, -1 }) {
			assertThatThrownBy(() -> Expense.record(group, "x", Money.eur(cents), date, alice, List.of(alice)))
					.isInstanceOfSatisfying(DomainException.class,
							e -> assertThat(e.code()).isEqualTo(ErrorCode.AMOUNT_NOT_POSITIVE));
		}
	}

	@Test
	void rejectsEmptyParticipantsNO_PARTICIPANTS() {
		assertThatThrownBy(() -> Expense.record(group, "x", Money.eur(100), date, alice, List.of()))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_PARTICIPANTS));
	}

	@Test
	void duplicateParticipantsAreRejected() {
		assertThatThrownBy(() -> Expense.record(group, "x", Money.eur(100), date, alice, List.of(alice, bob, alice)))
				.isInstanceOfSatisfying(DomainException.class,
						e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_PARTICIPANTS));
	}

}
