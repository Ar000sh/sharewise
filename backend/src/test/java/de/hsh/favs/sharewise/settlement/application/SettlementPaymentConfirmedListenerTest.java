package de.hsh.favs.sharewise.settlement.application;

import static org.mockito.Mockito.verify;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentId;
import de.hsh.favs.sharewise.shared.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The listener only hands the settlement context's event to the ledger service. */
@ExtendWith(MockitoExtension.class)
class SettlementPaymentConfirmedListenerTest {

	@Mock
	GroupLedgerService ledgers;

	@Test
	void aConfirmedPaymentIsHandedToTheLedgerService() {
		// given
		var event = new SettlementPaymentConfirmed(new SettlementPaymentId(UUID.randomUUID()),
				ExpenseRecordedFixture.GROUP, AccountFixture.bob().userId(), AccountFixture.alice().userId(),
				Money.eur("10.00"), MutableClock.START);

		// when
		new SettlementPaymentConfirmedListener(ledgers).on(event);

		// then
		verify(ledgers).bookSettlementPayment(event);
	}

}
