package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseId;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentId;
import de.hsh.favs.sharewise.settlement.domain.PaymentStatus;
import de.hsh.favs.sharewise.settlement.domain.SettlementPayment;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a mock cannot prove: the ledger lock serializes two records (D6), the reservation
 * query (D5), the optimistic lock on two answers, and results that are readable outside a
 * transaction. Real accounts and a real group; balances are seeded through
 * {@link GroupLedgerService#bookExpense}: Alice paid 300.00 for Alice, Bob and Carol, so
 * Bob and Carol each owe Alice 100.00.
 */
@ModuleIntegrationTest
class SettlementPaymentServiceDbTest {

	@Autowired
	SettlementPaymentService service;

	@Autowired
	SettlementPaymentRepository payments;

	@Autowired
	GroupLedgerService ledgers;

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
		ledgers.bookExpense(new ExpenseRecordedFixture().inGroup(trip)
			.paidBy(alice.userId())
			.withTotalAmount("300.00")
			.splitBetween(alice.userId(), bob.userId(), carol.userId())
			.build());
	}

	@Test
	void twoConcurrentRecordsOfTheSameDebtorCannotTogetherExceedTheDebt() throws Exception {
		// given two payments of 60.00 against Bob's 100.00, sent at the same moment
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<SettlementPayment>> calls = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			calls.add(pool.submit(() -> {
				start.await();
				return service.record(bobPaysAlice("60.00"));
			}));
		}

		// when
		start.countDown();
		List<Object> outcomes = new ArrayList<>();
		try {
			for (Future<SettlementPayment> call : calls) {
				try {
					outcomes.add(call.get(30, TimeUnit.SECONDS));
				}
				catch (ExecutionException e) {
					outcomes.add(e.getCause());
				}
			}
		}
		finally {
			pool.shutdown();
		}

		// then exactly one is recorded, the other is refused
		assertThat(outcomes).filteredOn(SettlementPayment.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(DomainException.class::isInstance)
			.singleElement()
			.satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.PAYMENT_EXCEEDS_OPEN_DEBT));
		assertThat(service.history(trip, alice.userId())).singleElement()
			.satisfies(p -> assertThat(p.amount()).isEqualTo(Money.eur("60.00")));
	}

	@Test
	void reservedSumCountsConfirmedUntilTheLedgerEntryExists() {
		// given Bob's 40.00 is PENDING, Carol's 30.00 is REJECTED and 20.00 of Carol's
		// debt is WAIVED
		// (built with the aggregate and saved directly: through the service the real
		// listener would book them at once; that path is SettlementBookingTest)
		SettlementPayment bobs = saved(SettlementPayment
			.record(trip, bob.userId(), alice.userId(), Money.eur("40.00"), Money.eur("100.00"), null,
					MutableClock.START)
			.payment());
		SettlementPayment carols = saved(SettlementPayment
			.record(trip, carol.userId(), alice.userId(), Money.eur("30.00"), Money.eur("100.00"), null,
					MutableClock.START)
			.payment());
		carols.reject(alice.userId(), "Not arrived", MutableClock.START);
		payments.save(carols);
		saved(SettlementPayment
			.waive(trip, alice.userId(), carol.userId(), Money.eur("20.00"), Money.eur("100.00"), "Gift",
					MutableClock.START)
			.payment());
		assertThat(reserved(bob, alice)).isEqualByComparingTo("40.00");
		assertThat(reserved(carol, alice)).isEqualByComparingTo("20.00");
		assertThat(reserved(null, alice)).isEqualByComparingTo("60.00");

		// when Alice confirms, the ledger has not booked it yet (D5)
		bobs.confirm(alice.userId(), MutableClock.START);
		payments.save(bobs);

		// then it is still reserved
		assertThat(reserved(bob, null)).isEqualByComparingTo("40.00");

		// when an entry references the payment id (as Task 5 will book it)
		ledgers.bookExpense(new ExpenseRecordedFixture().inGroup(trip)
			.withExpenseId(new ExpenseId(bobs.paymentId().value()))
			.paidBy(bob.userId())
			.withTotalAmount("40.00")
			.splitBetween(alice.userId())
			.build());

		// then it is no longer reserved: the balance carries it now
		assertThat(reserved(bob, alice)).isEqualByComparingTo("0.00");
		assertThat(reserved(null, alice)).isEqualByComparingTo("20.00");
	}

	@Test
	void confirmAndRejectAtOnceLeaveExactlyOneAnswer() {
		// given a PENDING payment that a confirm has already loaded
		SettlementPaymentId id = service.record(bobPaysAlice("40.00")).paymentId();
		TransactionTemplate confirmCall = new TransactionTemplate(transactions);
		TransactionTemplate rejectCall = new TransactionTemplate(transactions);
		rejectCall.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		confirmCall.executeWithoutResult(status -> {
			assertThat(payments.findById(id.value()).orElseThrow().status()).isEqualTo(PaymentStatus.PENDING);

			// when the reject commits in between, and then the confirm goes on with the
			// copy it loaded before
			rejectCall.executeWithoutResult(inner -> service.reject(alice.userId(), id, "Not arrived"));

			// then the confirm loses on the version column and gets the same answer as a
			// late confirm
			assertThatThrownBy(() -> service.confirm(alice.userId(), id)).isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_NOT_PENDING));
			status.setRollbackOnly();
		});
		SettlementPayment stored = payments.findById(id.value()).orElseThrow();
		assertThat(stored.status()).isEqualTo(PaymentStatus.REJECTED);
		assertThat(stored.note()).isEqualTo("Not arrived");
	}

	@Test
	void anApiPortCallOutsideATransactionDoesNotFailLazily() {
		// given
		SettlementPayment recorded = service.record(bobPaysAlice("40.00"));

		// when read from here, outside any transaction, like a controller does
		List<SettlementPayment> history = service.history(trip, carol.userId());
		List<SettlementPayment> inbox = service.awaitingMyAnswer(alice.userId());

		// then every field is a plain value
		assertThat(history).singleElement().satisfies(p -> {
			assertThat(p.paymentId()).isEqualTo(recorded.paymentId());
			assertThat(p.groupId()).isEqualTo(trip);
			assertThat(p.fromDebtor()).isEqualTo(bob.userId());
			assertThat(p.toCreditor()).isEqualTo(alice.userId());
			assertThat(p.amount()).isEqualTo(Money.eur("40.00"));
			assertThat(p.status()).isEqualTo(PaymentStatus.PENDING);
			assertThat(p.note()).isEqualTo("Bank transfer");
			assertThat(p.createdAt()).isNotNull();
			assertThat(p.resolvedAt()).isNull();
		});
		assertThat(inbox).extracting(SettlementPayment::paymentId).containsExactly(recorded.paymentId());
		assertThat(service.awaitingMyAnswer(bob.userId())).isEmpty();
	}

	@Test
	void aNewSettlementPaymentIsInsertedNotMerged() {
		// given a new payment; its id is assigned by the aggregate, so only the version
		// tells Spring Data that it is new
		SettlementPayment payment = SettlementPayment
			.record(trip, bob.userId(), alice.userId(), Money.eur("10.00"), Money.eur("100.00"), null,
					MutableClock.START)
			.payment();

		// when
		SettlementPayment saved = payments.save(payment);

		// then persist keeps the instance (a merge would return a copy after an extra
		// select), and the row is there
		assertThat(saved).isSameAs(payment);
		assertThat(payments.findById(payment.paymentId().value()))
			.hasValueSatisfying(stored -> assertThat(stored.status()).isEqualTo(PaymentStatus.PENDING));
	}

	private RecordSettlementPaymentCommand bobPaysAlice(String amount) {
		return new RecordSettlementPaymentCommand(trip, bob.userId(), alice.userId(), Money.eur(amount),
				"Bank transfer");
	}

	private SettlementPayment saved(SettlementPayment payment) {
		return payments.save(payment);
	}

	private BigDecimal reserved(AccountFixture debtor, AccountFixture creditor) {
		return payments.reserved(trip.value(), debtor == null ? null : debtor.userId().value(),
				creditor == null ? null : creditor.userId().value());
	}

}
