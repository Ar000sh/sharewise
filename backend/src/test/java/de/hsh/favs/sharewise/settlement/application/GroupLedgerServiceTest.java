package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.shared.events.AllBalancesSettled;
import de.hsh.favs.sharewise.shared.events.MemberBalanceStatusChanged;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Orchestration only: the ledger is created if absent, locked, and an expense is booked
 * once.
 */
@ExtendWith(MockitoExtension.class)
class GroupLedgerServiceTest {

	@Mock
	GroupLedgerRepository ledgers;

	@Mock
	ApplicationEventPublisher events;

	GroupLedgerService service;

	ExpenseRecorded event = new ExpenseRecordedFixture().build();

	GroupLedger ledger = new GroupLedger(event.groupId());

	UserId alice = AccountFixture.alice().userId();

	UserId bob = AccountFixture.bob().userId();

	UserId carol = AccountFixture.carol().userId();

	UserId david = AccountFixture.david().userId();

	@BeforeEach
	void setUp() {
		service = new GroupLedgerService(ledgers, events, Clock.fixed(MutableClock.START, ZoneOffset.UTC));
		when(ledgers.findForUpdate(event.groupId().value())).thenReturn(Optional.of(ledger));
	}

	@Test
	void anExpenseIsBookedAfterCreatingAndLockingTheLedger() {
		// given
		when(ledgers.isBooked(event.groupId().value(), event.expenseId().value())).thenReturn(false);

		// when
		service.bookExpense(event);

		// then
		var order = inOrder(ledgers);
		order.verify(ledgers).createIfAbsent(event.groupId().value());
		order.verify(ledgers).findForUpdate(event.groupId().value());
		order.verify(ledgers).isBooked(event.groupId().value(), event.expenseId().value());
		order.verify(ledgers).save(ledger);
		assertThat(ledger.entries()).hasSize(5);
		assertThat(ledger.entries()).allSatisfy(e -> assertThat(e.occurredAt()).isEqualTo(MutableClock.START));
	}

	@Test
	void anExpenseThatIsAlreadyBookedIsNotBookedAgain() {
		// given the event is delivered a second time
		when(ledgers.isBooked(event.groupId().value(), event.expenseId().value())).thenReturn(true);

		// when
		service.bookExpense(event);

		// then
		verify(ledgers, never()).save(any());
		assertThat(ledger.entries()).isEmpty();
	}

	@Test
	void bookingTheSameConfirmedPaymentTwiceBooksOnce() {
		// given the payment is booked by the first delivery
		var confirmed = new SettlementPaymentConfirmed(new SettlementPaymentId(UUID.randomUUID()), event.groupId(),
				AccountFixture.bob().userId(), AccountFixture.alice().userId(), Money.eur("10.00"), MutableClock.START);
		when(ledgers.isBooked(event.groupId().value(), confirmed.paymentId().value())).thenReturn(false, true);

		// when the event is delivered twice
		service.bookSettlementPayment(confirmed);
		service.bookSettlementPayment(confirmed);

		// then one pair of entries and one save
		verify(ledgers, times(1)).save(ledger);
		assertThat(ledger.entries()).hasSize(2);
		assertThat(ledger.entries()).allSatisfy(e -> assertThat(e.occurredAt()).isEqualTo(MutableClock.START));
	}

	@Test
	void bookingTheSameWaiverTwiceBooksOnce() {
		// given
		var waived = new DebtWaived(new SettlementPaymentId(UUID.randomUUID()), event.groupId(),
				AccountFixture.bob().userId(), AccountFixture.alice().userId(), Money.eur("10.00"), MutableClock.START);
		when(ledgers.isBooked(event.groupId().value(), waived.paymentId().value())).thenReturn(false, true);

		// when
		service.bookDebtWaiver(waived);
		service.bookDebtWaiver(waived);

		// then
		verify(ledgers, times(1)).save(ledger);
		assertThat(ledger.entries()).hasSize(2);
	}

	@Test
	void bookingCreatesTheLedgerIfNoExpenseCameFirst() {
		// given
		var confirmed = new SettlementPaymentConfirmed(new SettlementPaymentId(UUID.randomUUID()), event.groupId(),
				AccountFixture.bob().userId(), AccountFixture.alice().userId(), Money.eur("10.00"), MutableClock.START);

		// when
		service.bookSettlementPayment(confirmed);

		// then created, locked, checked, then saved
		var order = inOrder(ledgers);
		order.verify(ledgers).createIfAbsent(event.groupId().value());
		order.verify(ledgers).findForUpdate(event.groupId().value());
		order.verify(ledgers).isBooked(event.groupId().value(), confirmed.paymentId().value());
		order.verify(ledgers).save(ledger);
	}

	@Test
	void anExpenseThatMakesBalancesNonZeroPublishesOneStatusChangePerAffectedMember() {
		// given nobody has entries yet; after Alice's 900.00 split by four everyone is
		// non-zero
		when(ledgers.isBooked(event.groupId().value(), event.expenseId().value())).thenReturn(false);
		when(ledgers.balances(event.groupId())).thenReturn(List.of(), List.of(memberBalance(alice, "675.00"),
				memberBalance(bob, "-225.00"), memberBalance(carol, "-225.00"), memberBalance(david, "-225.00")));

		// when
		service.bookExpense(event);

		// then one change per person, and the group is not settled
		assertThat(published(4)).containsExactlyInAnyOrder(statusChanged(alice, false), statusChanged(bob, false),
				statusChanged(carol, false), statusChanged(david, false));
	}

	@Test
	void aPaymentThatBringsEveryoneToZeroPublishesAllBalancesSettledOnce() {
		// given Bob owes Alice 10.00; Carol has entries that already sum to zero
		var confirmed = bobPaysAlice("10.00");
		when(ledgers.isBooked(event.groupId().value(), confirmed.paymentId().value())).thenReturn(false);
		when(ledgers.balances(event.groupId())).thenReturn(
				List.of(memberBalance(alice, "10.00"), memberBalance(bob, "-10.00"), memberBalance(carol, "0.00")),
				List.of(memberBalance(alice, "0.00"), memberBalance(bob, "0.00"), memberBalance(carol, "0.00")));

		// when
		service.bookSettlementPayment(confirmed);

		// then Alice and Bob flip, Carol does not, and the group is settled once
		assertThat(published(3)).containsExactlyInAnyOrder(statusChanged(alice, true), statusChanged(bob, true),
				new AllBalancesSettled(event.groupId(), MutableClock.START));
	}

	@Test
	void nothingIsPublishedWhenNoZeroStateFlips() {
		// given Bob pays only half of the 10.00 he owes
		var confirmed = bobPaysAlice("5.00");
		when(ledgers.isBooked(event.groupId().value(), confirmed.paymentId().value())).thenReturn(false);
		when(ledgers.balances(event.groupId())).thenReturn(
				List.of(memberBalance(alice, "10.00"), memberBalance(bob, "-10.00")),
				List.of(memberBalance(alice, "5.00"), memberBalance(bob, "-5.00")));

		// when
		service.bookSettlementPayment(confirmed);

		// then
		verifyNoInteractions(events);
	}

	@Test
	void aRedeliveredBookingPublishesNothing() {
		// given the waiver is already booked
		var waived = new DebtWaived(new SettlementPaymentId(UUID.randomUUID()), event.groupId(), bob, alice,
				Money.eur("10.00"), MutableClock.START);
		when(ledgers.isBooked(event.groupId().value(), waived.paymentId().value())).thenReturn(true);

		// when
		service.bookDebtWaiver(waived);

		// then
		verify(ledgers, never()).save(any());
		verifyNoInteractions(events);
	}

	private SettlementPaymentConfirmed bobPaysAlice(String amount) {
		return new SettlementPaymentConfirmed(new SettlementPaymentId(UUID.randomUUID()), event.groupId(), bob, alice,
				Money.eur(amount), MutableClock.START);
	}

	private MemberBalanceStatusChanged statusChanged(UserId user, boolean hasZeroBalance) {
		return new MemberBalanceStatusChanged(event.groupId(), user, hasZeroBalance, MutableClock.START);
	}

	private List<Object> published(int count) {
		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(events, times(count)).publishEvent(captor.capture());
		return captor.getAllValues();
	}

	private static MemberBalance memberBalance(UserId user, String amount) {
		return new MemberBalance(user, Money.eur(amount));
	}

}
