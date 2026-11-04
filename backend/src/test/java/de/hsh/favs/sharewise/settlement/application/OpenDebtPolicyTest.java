package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The open debt rule (D3, D5, R17): the plan edge of the pair, the debt of the debtor and
 * the credit of the creditor, each reduced by what is already reserved, and never below
 * zero. Balances are the Mallorca numbers (Alice +630.00, Bob -90.00, Carol -270.00,
 * David -270.00) unless a case needs a second creditor. What counts as reserved is the
 * repository's query (proved in {@link SettlementPaymentServiceDbTest}); here it is
 * stubbed.
 */
@ExtendWith(MockitoExtension.class)
class OpenDebtPolicyTest {

	static final UserId ALICE = AccountFixture.alice().userId();

	static final UserId BOB = AccountFixture.bob().userId();

	static final UserId CAROL = AccountFixture.carol().userId();

	static final UserId DAVID = AccountFixture.david().userId();

	static final GroupId GROUP = new GroupId(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"));

	@Mock
	GroupLedgerRepository ledgers;

	@Mock
	SettlementPaymentRepository payments;

	OpenDebtPolicy policy;

	@BeforeEach
	void setUp() {
		policy = new OpenDebtPolicy(ledgers, payments);
		// nothing reserved unless a test says otherwise
		when(payments.reserved(any(), any(), any())).thenReturn(BigDecimal.ZERO);
	}

	@Test
	void fullDebtToTheOnlyCreditorIsOpen() {
		// given
		mallorca();

		// when
		Money open = policy.openDebt(GROUP, CAROL, ALICE);

		// then
		assertThat(open).isEqualTo(Money.eur("270.00"));
	}

	@Test
	void pendingPaymentsReserveTheDebtOfThatPair() {
		// given Dora owes Alice 50.00 and Bob 40.00; 30.00 to Alice is PENDING
		UserId dora = new UserId(UUID.randomUUID());
		balances(total(ALICE, "50.00"), total(BOB, "40.00"), total(dora, "-90.00"));
		reserved(dora, ALICE, "30.00");
		reserved(dora, null, "30.00");
		reserved(null, ALICE, "30.00");

		// when
		Money open = policy.openDebt(GROUP, dora, ALICE);

		// then 20.00 is left, so a second 30.00 to Alice is above the open debt
		assertThat(open).isEqualTo(Money.eur("20.00"));
		assertThat(Money.eur("30.00").minus(open).isPositive()).isTrue();
	}

	@Test
	void payingBTheOtherCreditorIsStillPossible() {
		// given the same group: 30.00 of Dora's 50.00 to Alice is PENDING
		UserId dora = new UserId(UUID.randomUUID());
		balances(total(ALICE, "50.00"), total(BOB, "40.00"), total(dora, "-90.00"));
		reserved(dora, null, "30.00");

		// when
		Money open = policy.openDebt(GROUP, dora, BOB);

		// then the debt to Bob is untouched: min(40.00, 90.00 - 30.00, 40.00)
		assertThat(open).isEqualTo(Money.eur("40.00"));
	}

	@Test
	void rejectedPaymentFreesTheReservation() {
		// given Carol's 270.00 to Alice was rejected: the balances did not move and the
		// repository no longer counts it as reserved
		mallorca();

		// when
		Money open = policy.openDebt(GROUP, CAROL, ALICE);

		// then
		assertThat(open).isEqualTo(Money.eur("270.00"));
	}

	@Test
	void aVanishedPlanEdgeClampsToZero() {
		// given Carol is at zero after a new expense, but 270.00 of hers is still PENDING
		balances(total(ALICE, "360.00"), total(BOB, "-90.00"), total(CAROL, "0.00"), total(DAVID, "-270.00"));
		reserved(CAROL, ALICE, "270.00");
		reserved(CAROL, null, "270.00");
		reserved(null, ALICE, "270.00");

		// when
		Money open = policy.openDebt(GROUP, CAROL, ALICE);

		// then zero, never a negative open debt
		assertThat(open).isEqualTo(Money.eur("0.00"));
	}

	@Test
	void aPairWithoutAnEdgeHasNoOpenDebt() {
		// given Carol and David both owe, nobody owes Carol
		mallorca();

		// when
		Money open = policy.openDebt(GROUP, DAVID, CAROL);

		// then
		assertThat(open).isEqualTo(Money.eur("0.00"));
	}

	@Test
	void creditorCreditCapsThePayment() {
		// given payments recorded under an earlier plan already reserve 500.00 of Alice's
		// 630.00 credit
		mallorca();
		reserved(null, ALICE, "500.00");

		// when
		Money open = policy.openDebt(GROUP, DAVID, ALICE);

		// then min(270.00, 270.00, 630.00 - 500.00)
		assertThat(open).isEqualTo(Money.eur("130.00"));
	}

	@Test
	void aConfirmedPaymentNotYetBookedStillReserves() {
		// given Carol's 270.00 is CONFIRMED but not booked yet: the balances are
		// unchanged and the repository still counts it (D5)
		mallorca();
		reserved(CAROL, ALICE, "270.00");
		reserved(CAROL, null, "270.00");
		reserved(null, ALICE, "270.00");

		// when
		Money open = policy.openDebt(GROUP, CAROL, ALICE);

		// then paying again before the booking is impossible
		assertThat(open).isEqualTo(Money.eur("0.00"));
	}

	@Test
	void aGroupWithoutLedgerEntriesHasNoOpenDebt() {
		// given no expense was ever booked
		balances();

		// when
		Money open = policy.openDebt(GROUP, CAROL, ALICE);

		// then
		assertThat(open).isEqualTo(Money.eur("0.00"));
	}

	private void mallorca() {
		balances(total(ALICE, "630.00"), total(BOB, "-90.00"), total(CAROL, "-270.00"), total(DAVID, "-270.00"));
	}

	private void balances(MemberBalance... balances) {
		when(ledgers.balances(GROUP)).thenReturn(List.of(balances));
	}

	private void reserved(UserId debtor, UserId creditor, String amount) {
		when(payments.reserved(GROUP.value(), debtor == null ? null : debtor.value(),
				creditor == null ? null : creditor.value()))
			.thenReturn(new BigDecimal(amount));
	}

	private static MemberBalance total(UserId user, String amount) {
		return new MemberBalance(user, Money.eur(amount));
	}

}
