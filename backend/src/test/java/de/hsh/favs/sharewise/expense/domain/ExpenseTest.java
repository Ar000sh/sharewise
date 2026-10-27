package de.hsh.favs.sharewise.expense.domain;

import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExpenseTest {

	@Test
	void recordsExpenseWithEqualSharesInParticipantOrder() {
		// given / when
		Expense expense = new ExpenseFixture().withAmountCents(90_001)
			.withPayer(BOB)
			.withParticipants(ALICE, BOB)
			.build();

		// then
		assertThat(expense.id()).isNotNull();
		assertThat(expense.payerId()).isEqualTo(BOB);
		assertThat(expense.shares()).extracting(Share::memberId).containsExactly(ALICE, BOB);
		assertThat(expense.shares()).extracting(s -> s.amount().cents()).containsExactly(45_001L, 45_000L);
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, -1 })
	void zeroAndNegativeAmountsAreRejected(long cents) {
		// given
		var fixture = new ExpenseFixture().withAmountCents(cents);

		// when / then
		assertRejectedWith(fixture, ErrorCode.AMOUNT_NOT_POSITIVE);
	}

	@Test
	void noParticipantsIsRejected() {
		assertRejectedWith(new ExpenseFixture().withParticipants(), ErrorCode.NO_PARTICIPANTS);
	}

	@Test
	void duplicateParticipantsAreRejectedInsteadOfChargedTwice() {
		assertRejectedWith(new ExpenseFixture().withParticipants(ALICE, BOB, ALICE), ErrorCode.NO_PARTICIPANTS);
	}

	private static void assertRejectedWith(ExpenseFixture fixture, ErrorCode code) {
		assertThatThrownBy(fixture::build).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(code));
	}

}
