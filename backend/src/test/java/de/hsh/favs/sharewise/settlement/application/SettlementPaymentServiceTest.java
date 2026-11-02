package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentId;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRecorded;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.domain.PaymentStatus;
import de.hsh.favs.sharewise.settlement.domain.SettlementPayment;
import de.hsh.favs.sharewise.settlement.domain.SettlementPaymentFixture;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Orchestration of the settlement payment use cases: the order of the checks, the ledger
 * lock before the open debt (D6), save and publish, and that a refused call leaves
 * nothing behind. The rules themselves are tested in {@code SettlementPaymentTest} and
 * {@link OpenDebtPolicyTest}.
 */
@ExtendWith(MockitoExtension.class)
class SettlementPaymentServiceTest {

	static final GroupId GROUP = SettlementPaymentFixture.GROUP;

	static final UserId ALICE = SettlementPaymentFixture.ALICE;

	static final UserId BOB = SettlementPaymentFixture.BOB;

	@Mock
	GroupMembership groups;

	@Mock
	SettlementPaymentRepository payments;

	@Mock
	GroupLedgerRepository ledgers;

	@Mock
	OpenDebtPolicy openDebt;

	@Mock
	ApplicationEventPublisher events;

	SettlementPaymentService service;

	@BeforeEach
	void setUp() {
		service = new SettlementPaymentService(groups, payments, ledgers, openDebt, events,
				Clock.fixed(MutableClock.START, ZoneOffset.UTC));
	}

	@Test
	void recordTakesTheLedgerLockBeforeComputingOpenDebt() {
		// given
		openGroupWithBobOwingAlice("300.00");

		// when
		service.record(bobPaysAlice("270.00"));

		// then
		var order = inOrder(groups, ledgers, openDebt, payments, events);
		order.verify(groups).requireMember(GROUP, BOB);
		order.verify(ledgers).createIfAbsent(GROUP.value());
		order.verify(ledgers).findForUpdate(GROUP.value());
		order.verify(openDebt).openDebt(GROUP, BOB, ALICE);
		order.verify(payments).save(any(SettlementPayment.class));
		order.verify(events).publishEvent(any(SettlementPaymentRecorded.class));
	}

	@Test
	void recordSavesAndPublishesTheRecordedEvent() {
		// given
		openGroupWithBobOwingAlice("300.00");

		// when
		SettlementPayment result = service.record(bobPaysAlice("270.00"));

		// then
		var saved = ArgumentCaptor.forClass(SettlementPayment.class);
		verify(payments).save(saved.capture());
		assertThat(saved.getValue()).isSameAs(result);
		assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
		assertThat(result.amount()).isEqualTo(Money.eur("270.00"));
		assertThat(result.note()).isEqualTo("Bank transfer");
		var published = ArgumentCaptor.forClass(SettlementPaymentRecorded.class);
		verify(events).publishEvent(published.capture());
		assertThat(published.getValue()).isEqualTo(new SettlementPaymentRecorded(result.paymentId(), GROUP, BOB, ALICE,
				Money.eur("270.00"), MutableClock.START));
	}

	@Test
	void aRefusedRecordSavesAndPublishesNothing() {
		// given only 100.00 is open
		openGroupWithBobOwingAlice("100.00");

		// when / then
		assertCode(() -> service.record(bobPaysAlice("270.00")), ErrorCode.PAYMENT_EXCEEDS_OPEN_DEBT);
		verify(payments, never()).save(any());
		verifyNoInteractions(events);
	}

	@Test
	void recordToSomeoneOutsideTheGroupIsRefusedBeforeTheLock() {
		// given
		when(groups.status(GROUP)).thenReturn(GroupStatus.OPEN);
		when(groups.isMember(GROUP, ALICE)).thenReturn(false);

		// when / then
		assertCode(() -> service.record(bobPaysAlice("270.00")), ErrorCode.PARTICIPANT_NOT_IN_GROUP);
		verifyNoInteractions(ledgers, openDebt, payments, events);
	}

	@Test
	void archivedGroupRefusesEveryOperation() {
		// given
		when(groups.status(GROUP)).thenReturn(GroupStatus.ARCHIVED);
		SettlementPayment pending = new SettlementPaymentFixture().build();
		when(payments.findById(pending.paymentId().value())).thenReturn(Optional.of(pending));
		when(groups.isMember(GROUP, ALICE)).thenReturn(true);

		// when / then
		assertCode(() -> service.record(bobPaysAlice("10.00")), ErrorCode.GROUP_NOT_OPEN);
		assertCode(() -> service.waive(new WaiveDebtCommand(GROUP, ALICE, BOB, Money.eur("10.00"), "Gift")),
				ErrorCode.GROUP_NOT_OPEN);
		assertCode(() -> service.confirm(ALICE, pending.paymentId()), ErrorCode.GROUP_NOT_OPEN);
		assertCode(() -> service.reject(ALICE, pending.paymentId(), "Not arrived"), ErrorCode.GROUP_NOT_OPEN);
		assertThat(pending.status()).isEqualTo(PaymentStatus.PENDING);
		verify(payments, never()).save(any());
		verify(payments, never()).saveAndFlush(any());
		verifyNoInteractions(ledgers, openDebt, events);
	}

	@Test
	void confirmOfAnUnknownPaymentIsNotFoundAndPublishesNothing() {
		// given
		var unknown = new SettlementPaymentId(UUID.randomUUID());
		when(payments.findById(unknown.value())).thenReturn(Optional.empty());

		// when / then
		assertCode(() -> service.confirm(ALICE, unknown), ErrorCode.SETTLEMENT_PAYMENT_NOT_FOUND);
		verify(payments, never()).saveAndFlush(any());
		verifyNoInteractions(events);
	}

	@Test
	void confirmBySomeoneOutsideThePaymentsGroupIsNotFound() {
		// given the payment exists, but the caller is not in its group
		SettlementPayment pending = new SettlementPaymentFixture().build();
		UserId outsider = new UserId(UUID.randomUUID());
		when(payments.findById(pending.paymentId().value())).thenReturn(Optional.of(pending));
		when(groups.isMember(GROUP, outsider)).thenReturn(false);

		// when / then
		assertCode(() -> service.confirm(outsider, pending.paymentId()), ErrorCode.SETTLEMENT_PAYMENT_NOT_FOUND);
		assertThat(pending.status()).isEqualTo(PaymentStatus.PENDING);
		verify(payments, never()).saveAndFlush(any());
		verifyNoInteractions(events);
	}

	private void openGroupWithBobOwingAlice(String open) {
		when(groups.status(GROUP)).thenReturn(GroupStatus.OPEN);
		when(groups.isMember(GROUP, ALICE)).thenReturn(true);
		when(ledgers.findForUpdate(GROUP.value())).thenReturn(Optional.of(new GroupLedger(GROUP)));
		when(openDebt.openDebt(GROUP, BOB, ALICE)).thenReturn(Money.eur(open));
	}

	private static RecordSettlementPaymentCommand bobPaysAlice(String amount) {
		return new RecordSettlementPaymentCommand(GROUP, BOB, ALICE, Money.eur(amount), "Bank transfer");
	}

	private static void assertCode(ThrowingCallable call, ErrorCode code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(code));
	}

}
