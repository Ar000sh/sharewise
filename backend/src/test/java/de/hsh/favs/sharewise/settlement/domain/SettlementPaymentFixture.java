package de.hsh.favs.sharewise.settlement.domain;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Test data for a {@link SettlementPayment}. By default: Bob pays Alice 270.00 EUR of the
 * 300.00 EUR he owes her, with the note "Bank transfer", at {@link MutableClock#START}.
 * The result always goes through {@link SettlementPayment#record}.
 */
public class SettlementPaymentFixture {

	public static final UserId ALICE = AccountFixture.alice().userId();

	public static final UserId BOB = AccountFixture.bob().userId();

	public static final UserId CAROL = AccountFixture.carol().userId();

	public static final GroupId GROUP = new GroupId(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"));

	private UserId fromDebtor = BOB;

	private UserId toCreditor = ALICE;

	private Money amount = Money.eur("270.00");

	private Money openDebt = Money.eur("300.00");

	private String note = "Bank transfer";

	private Instant now = MutableClock.START;

	public SettlementPaymentFixture withFromDebtor(UserId fromDebtor) {
		this.fromDebtor = fromDebtor;
		return this;
	}

	public SettlementPaymentFixture withToCreditor(UserId toCreditor) {
		this.toCreditor = toCreditor;
		return this;
	}

	public SettlementPaymentFixture withAmount(String amount) {
		this.amount = Money.eur(amount);
		return this;
	}

	public SettlementPaymentFixture withOpenDebt(String openDebt) {
		this.openDebt = Money.eur(openDebt);
		return this;
	}

	public SettlementPaymentFixture withNote(String note) {
		this.note = note;
		return this;
	}

	public SettlementPaymentFixture withNow(Instant now) {
		this.now = now;
		return this;
	}

	public SettlementPayment.Recorded record() {
		return SettlementPayment.record(GROUP, fromDebtor, toCreditor, amount, openDebt, note, now);
	}

	public SettlementPayment build() {
		return record().payment();
	}

	/**
	 * A debt waiver: the creditor forgives the fixture's amount, the note is the reason.
	 */
	public SettlementPayment.Waived waive() {
		return SettlementPayment.waive(GROUP, toCreditor, fromDebtor, amount, openDebt, note, now);
	}

}
