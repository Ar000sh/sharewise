package de.hsh.favs.sharewise.settlement.domain;

import static de.hsh.favs.sharewise.settlement.domain.SettlementPaymentFixture.ALICE;
import static de.hsh.favs.sharewise.settlement.domain.SettlementPaymentFixture.BOB;
import static de.hsh.favs.sharewise.settlement.domain.SettlementPaymentFixture.CAROL;
import static de.hsh.favs.sharewise.settlement.domain.SettlementPaymentFixture.GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRecorded;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRejected;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The rules of the SettlementPayment aggregate (R16, R17). No Spring, no database. */
class SettlementPaymentTest {

	private static final Instant LATER = MutableClock.START.plus(Duration.ofHours(2));

	private static void assertRefusedWith(Runnable action, ErrorCode code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(code));
	}

	@Test
	void recordedPaymentIsPendingAndHoldsTheAmount() {
		// when
		SettlementPayment.Recorded recorded = new SettlementPaymentFixture().record();

		// then
		SettlementPayment payment = recorded.payment();
		assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
		assertThat(payment.groupId()).isEqualTo(GROUP);
		assertThat(payment.fromDebtor()).isEqualTo(BOB);
		assertThat(payment.toCreditor()).isEqualTo(ALICE);
		assertThat(payment.amount()).isEqualTo(Money.eur("270.00"));
		assertThat(payment.note()).isEqualTo("Bank transfer");
		assertThat(payment.createdAt()).isEqualTo(MutableClock.START);
		assertThat(payment.resolvedAt()).isNull();
		assertThat(payment.paymentId()).isNotNull();
	}

	@Test
	void amountEqualToOpenDebtIsAllowed() {
		// when
		SettlementPayment payment = new SettlementPaymentFixture().withAmount("300.00").build();

		// then
		assertThat(payment.amount()).isEqualTo(Money.eur("300.00"));
		assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
	}

	@Test
	void amountOneCentAboveOpenDebtIsRejectedAndNothingIsCreated() {
		// given
		SettlementPaymentFixture fixture = new SettlementPaymentFixture().withAmount("300.01");

		// when / then
		assertRefusedWith(fixture::record, ErrorCode.PAYMENT_EXCEEDS_OPEN_DEBT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.00", "-1.00" })
	void zeroAndNegativeAmountsAreNotPositive(String amount) {
		// given
		SettlementPaymentFixture fixture = new SettlementPaymentFixture().withAmount(amount);

		// when / then
		assertRefusedWith(fixture::record, ErrorCode.AMOUNT_NOT_POSITIVE);
		assertRefusedWith(fixture::waive, ErrorCode.AMOUNT_NOT_POSITIVE);
	}

	@Test
	void payingYourselfIsRefused() {
		// given
		SettlementPaymentFixture fixture = new SettlementPaymentFixture().withFromDebtor(ALICE);

		// when / then
		assertRefusedWith(fixture::record, ErrorCode.BAD_REQUEST);
		assertRefusedWith(fixture::waive, ErrorCode.BAD_REQUEST);
	}

	@Test
	void aNoteOverFiveHundredCharactersIsRefused() {
		// given
		SettlementPaymentFixture fixture = new SettlementPaymentFixture().withNote("x".repeat(501));

		// when / then
		assertRefusedWith(fixture::record, ErrorCode.BAD_REQUEST);
	}

	@Test
	void theNoteOfARecordedPaymentIsOptional() {
		// when
		SettlementPayment payment = new SettlementPaymentFixture().withNote(null).build();

		// then
		assertThat(payment.note()).isNull();
	}

	@Test
	void aCurrencyMismatchWithTheOpenDebtIsBadRequest() {
		// when / then
		assertRefusedWith(
				() -> SettlementPayment.record(GROUP, BOB, ALICE, Money.eur("10.00"),
						Money.ofMinorUnits(1000, java.util.Currency.getInstance("USD")), null, MutableClock.START),
				ErrorCode.BAD_REQUEST);
	}

	@Test
	void onlyTheCreditorCanConfirm() {
		// given
		SettlementPayment payment = new SettlementPaymentFixture().build();

		// when / then
		assertRefusedWith(() -> payment.confirm(BOB, LATER), ErrorCode.NOT_THE_CREDITOR);
		assertRefusedWith(() -> payment.confirm(CAROL, LATER), ErrorCode.NOT_THE_CREDITOR);
		assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
		assertThat(payment.resolvedAt()).isNull();
	}

	@Test
	void creditorConfirmsAndTheEventCarriesTheDetails() {
		// given
		SettlementPayment payment = new SettlementPaymentFixture().build();

		// when
		SettlementPaymentConfirmed event = payment.confirm(ALICE, LATER);

		// then
		assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
		assertThat(payment.resolvedAt()).isEqualTo(LATER);
		assertThat(event).isEqualTo(
				new SettlementPaymentConfirmed(payment.paymentId(), GROUP, BOB, ALICE, Money.eur("270.00"), LATER));
	}

	@Test
	void confirmingTwiceIsPaymentNotPending() {
		// given
		SettlementPayment payment = new SettlementPaymentFixture().build();
		payment.confirm(ALICE, LATER);

		// when / then
		assertRefusedWith(() -> payment.confirm(ALICE, LATER.plusSeconds(1)), ErrorCode.PAYMENT_NOT_PENDING);
		assertThat(payment.resolvedAt()).isEqualTo(LATER);
	}

	@Test
	void rejectNeedsANoteAndSetsRejected() {
		// given
		SettlementPayment payment = new SettlementPaymentFixture().build();

		// when / then the note is required
		assertRefusedWith(() -> payment.reject(ALICE, "  ", LATER), ErrorCode.BAD_REQUEST);
		assertRefusedWith(() -> payment.reject(ALICE, null, LATER), ErrorCode.BAD_REQUEST);
		assertRefusedWith(() -> payment.reject(ALICE, "x".repeat(501), LATER), ErrorCode.BAD_REQUEST);
		assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);

		// when
		SettlementPaymentRejected event = payment.reject(ALICE, "Money never arrived", LATER);

		// then
		assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
		assertThat(payment.note()).isEqualTo("Money never arrived");
		assertThat(payment.resolvedAt()).isEqualTo(LATER);
		assertThat(event).isEqualTo(
				new SettlementPaymentRejected(payment.paymentId(), GROUP, BOB, ALICE, Money.eur("270.00"), LATER));
	}

	@Test
	void onlyTheCreditorCanReject() {
		// given
		SettlementPayment payment = new SettlementPaymentFixture().build();

		// when / then
		assertRefusedWith(() -> payment.reject(BOB, "No", LATER), ErrorCode.NOT_THE_CREDITOR);
		assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
	}

	@Test
	void aRejectedPaymentCannotBeConfirmed() {
		// given
		SettlementPayment payment = new SettlementPaymentFixture().build();
		payment.reject(ALICE, "Money never arrived", LATER);

		// when / then
		assertRefusedWith(() -> payment.confirm(ALICE, LATER.plusSeconds(1)), ErrorCode.PAYMENT_NOT_PENDING);
		assertRefusedWith(() -> payment.reject(ALICE, "Again", LATER.plusSeconds(1)), ErrorCode.PAYMENT_NOT_PENDING);
		assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
	}

	@Test
	void waiveCreatesAFinishedWaivedPaymentWithoutConfirmation() {
		// when alice forgives 90.00 of what bob owes her
		SettlementPayment.Waived waived = new SettlementPaymentFixture().withAmount("90.00")
			.withNote("Bob gets it as a gift")
			.waive();

		// then
		SettlementPayment payment = waived.payment();
		assertThat(payment.status()).isEqualTo(PaymentStatus.WAIVED);
		assertThat(payment.fromDebtor()).isEqualTo(BOB);
		assertThat(payment.toCreditor()).isEqualTo(ALICE);
		assertThat(payment.amount()).isEqualTo(Money.eur("90.00"));
		assertThat(payment.note()).isEqualTo("Bob gets it as a gift");
		assertThat(payment.resolvedAt()).isEqualTo(MutableClock.START);
		assertRefusedWith(() -> payment.confirm(ALICE, LATER), ErrorCode.PAYMENT_NOT_PENDING);
	}

	@Test
	void waiveAboveOpenDebtIsRejected() {
		// given
		SettlementPaymentFixture fixture = new SettlementPaymentFixture().withAmount("300.01");

		// when / then
		assertRefusedWith(fixture::waive, ErrorCode.PAYMENT_EXCEEDS_OPEN_DEBT);
	}

	@Test
	void waiveNeedsNoReason() {
		// when alice forgives without giving a reason
		SettlementPayment payment = new SettlementPaymentFixture().withNote(null).waive().payment();

		// then it is a finished WAIVED payment with no note
		assertThat(payment.status()).isEqualTo(PaymentStatus.WAIVED);
		assertThat(payment.note()).isNull();
	}

	@Test
	void waiveRefusesAReasonLongerThan500Characters() {
		// given
		SettlementPaymentFixture fixture = new SettlementPaymentFixture().withNote("x".repeat(501));

		// when / then
		assertRefusedWith(fixture::waive, ErrorCode.BAD_REQUEST);
	}

	@Test
	void eventsCarryIdsAmountAndTime() {
		// when
		SettlementPayment.Recorded recorded = new SettlementPaymentFixture().record();
		SettlementPayment.Waived waived = new SettlementPaymentFixture().withAmount("90.00").waive();

		// then
		assertThat(recorded.event()).isEqualTo(new SettlementPaymentRecorded(recorded.payment().paymentId(), GROUP, BOB,
				ALICE, Money.eur("270.00"), MutableClock.START));
		assertThat(waived.event()).isEqualTo(new DebtWaived(waived.payment().paymentId(), GROUP, BOB, ALICE,
				Money.eur("90.00"), MutableClock.START));
	}

}
