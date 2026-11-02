package de.hsh.favs.sharewise.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import de.hsh.favs.sharewise.settlement.application.RecordSettlementPaymentCommand;
import de.hsh.favs.sharewise.settlement.application.SettlementPaymentService;
import de.hsh.favs.sharewise.settlement.application.WaiveDebtCommand;
import de.hsh.favs.sharewise.settlement.domain.LedgerEntry;
import de.hsh.favs.sharewise.settlement.domain.SettlementPayment;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The event-driven booking of confirmed payments and waivers (R14, R16, R17, D7, D8).
 * Real accounts and a real group; Alice paid 300.00 for Alice, Bob and Carol (seeded
 * through the ledger service), so Bob and Carol each owe Alice 100.00. Booking happens
 * asynchronously after the commit of the service call, so every assertion waits for it.
 */
@ModuleIntegrationTest
class SettlementBookingTest {

	@Autowired
	SettlementPaymentService service;

	@Autowired
	SettlementPaymentRepository payments;

	@Autowired
	GroupLedgerService ledgerService;

	@Autowired
	GroupLedgerRepository ledgers;

	@Autowired
	Balances balances;

	@Autowired
	PlatformTransactionManager transactions;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	AccountFixture alice;

	AccountFixture bob;

	AccountFixture carol;

	GroupId trip;

	@BeforeEach
	void setUp() throws Exception {
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		carol = AccountFixture.signUp(mvc, json, "Carol");
		trip = new GroupId(new GroupRequests(mvc, json).groupWith(alice, "Trip " + UUID.randomUUID(), bob, carol));
		ledgerService.bookExpense(new ExpenseRecordedFixture().inGroup(trip)
			.paidBy(alice.userId())
			.withTotalAmount("300.00")
			.splitBetween(alice.userId(), bob.userId(), carol.userId())
			.build());
	}

	@Test
	void confirmingAPaymentMovesBothBalancesAndTheSumStaysZero() {
		// given Bob reported 40.00
		SettlementPayment payment = service.record(bobPaysAlice("40.00"));

		// when Alice confirms
		service.confirm(alice.userId(), payment.paymentId());

		// then the ledger books it: Bob owes 60.00, Alice is owed 160.00, Carol is
		// untouched
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balances.of(trip)).containsExactlyInAnyOrder(
					new MemberBalance(alice.userId(), Money.eur("160.00")),
					new MemberBalance(bob.userId(), Money.eur("-60.00")),
					new MemberBalance(carol.userId(), Money.eur("-100.00"))));
		assertThat(sumOfBalances()).isEqualTo(Money.eur("0.00"));
	}

	@Test
	void rejectingAPaymentChangesNoBalance() {
		// given
		SettlementPayment payment = service.record(bobPaysAlice("40.00"));

		// when
		service.reject(alice.userId(), payment.paymentId(), "Not arrived");

		// then nothing is booked, and stays so for a while (booking would be
		// asynchronous)
		await().during(Duration.ofMillis(500))
			.atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> assertThat(balances.of(trip)).containsExactlyInAnyOrder(
					new MemberBalance(alice.userId(), Money.eur("200.00")),
					new MemberBalance(bob.userId(), Money.eur("-100.00")),
					new MemberBalance(carol.userId(), Money.eur("-100.00"))));
		assertThat(entriesReferencing(payment)).isEmpty();
	}

	@Test
	void aWaiverMovesBalancesWithoutAConfirmation() {
		// when Alice forgives 30.00 of Carol's debt
		SettlementPayment waiver = service
			.waive(new WaiveDebtCommand(trip, alice.userId(), carol.userId(), Money.eur("30.00"), "Gift"));

		// then
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balances.of(trip)).containsExactlyInAnyOrder(
					new MemberBalance(alice.userId(), Money.eur("170.00")),
					new MemberBalance(bob.userId(), Money.eur("-100.00")),
					new MemberBalance(carol.userId(), Money.eur("-70.00"))));
		assertThat(sumOfBalances()).isEqualTo(Money.eur("0.00"));
		assertThat(entriesReferencing(waiver)).hasSize(2);
	}

	@Test
	void redeliveringTheConfirmedEventBooksOnce() {
		// given a confirmed payment that the ledger has booked
		SettlementPayment payment = service.record(bobPaysAlice("40.00"));
		service.confirm(alice.userId(), payment.paymentId());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(entriesReferencing(payment)).hasSize(2));

		// when the same event is delivered again, twice
		var again = new SettlementPaymentConfirmed(payment.paymentId(), trip, bob.userId(), alice.userId(),
				Money.eur("40.00"), payment.resolvedAt());
		ledgerService.bookSettlementPayment(again);
		ledgerService.bookSettlementPayment(again);

		// then still one pair of entries and the same balances
		assertThat(entriesReferencing(payment)).hasSize(2);
		assertThat(balances.of(trip)).containsExactlyInAnyOrder(new MemberBalance(alice.userId(), Money.eur("160.00")),
				new MemberBalance(bob.userId(), Money.eur("-60.00")),
				new MemberBalance(carol.userId(), Money.eur("-100.00")));
	}

	@Test
	void aBookedConfirmedPaymentNoLongerReservesAnyOpenDebt() {
		// given Bob's 40.00 is pending: it reserves, so only 60.00 of the 100.00 is open
		SettlementPayment payment = service.record(bobPaysAlice("40.00"));
		assertThat(payments.reserved(trip.value(), bob.userId().value(), alice.userId().value()))
			.isEqualByComparingTo("40.00");
		assertThatThrownBy(() -> service.record(bobPaysAlice("60.01"))).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_EXCEEDS_OPEN_DEBT));

		// when Alice confirms and the real booking has run
		service.confirm(alice.userId(), payment.paymentId());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(entriesReferencing(payment)).hasSize(2));

		// then the payment is no longer reserved: the balance carries it. The open debt
		// is
		// 100.00 - 40.00 = 60.00, not 20.00 (which is what counting it twice would give)
		assertThat(payments.reserved(trip.value(), bob.userId().value(), alice.userId().value()))
			.isEqualByComparingTo("0.00");
		assertThat(service.record(bobPaysAlice("60.00")).amount()).isEqualTo(Money.eur("60.00"));
	}

	@Test
	void aBookedWaiverNoLongerReservesAnyOpenDebt() {
		// given Alice waived 30.00 of Carol's 100.00
		SettlementPayment waiver = service
			.waive(new WaiveDebtCommand(trip, alice.userId(), carol.userId(), Money.eur("30.00"), "Gift"));
		assertThat(payments.reserved(trip.value(), carol.userId().value(), alice.userId().value()))
			.isEqualByComparingTo("30.00");

		// when the real booking has run
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(entriesReferencing(waiver)).hasSize(2));

		// then the open debt is 70.00 (not 40.00): 70.01 is refused, 70.00 is accepted
		assertThat(payments.reserved(trip.value(), carol.userId().value(), alice.userId().value()))
			.isEqualByComparingTo("0.00");
		assertThatThrownBy(() -> service.record(carolPaysAlice("70.01"))).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_EXCEEDS_OPEN_DEBT));
		assertThat(service.record(carolPaysAlice("70.00")).amount()).isEqualTo(Money.eur("70.00"));
	}

	private RecordSettlementPaymentCommand bobPaysAlice(String amount) {
		return new RecordSettlementPaymentCommand(trip, bob.userId(), alice.userId(), Money.eur(amount), null);
	}

	private RecordSettlementPaymentCommand carolPaysAlice(String amount) {
		return new RecordSettlementPaymentCommand(trip, carol.userId(), alice.userId(), Money.eur(amount), null);
	}

	private Money sumOfBalances() {
		return balances.of(trip)
			.stream()
			.map(MemberBalance::netBalance)
			.reduce(Money.eur(BigDecimal.ZERO), Money::plus);
	}

	private List<LedgerEntry> entriesReferencing(SettlementPayment payment) {
		return new TransactionTemplate(transactions).execute(status -> ledgers.findById(trip.value())
			.map(ledger -> ledger.entries()
				.stream()
				.filter(entry -> entry.referenceId().equals(payment.paymentId().value()))
				.toList())
			.orElse(List.of()));
	}

}
