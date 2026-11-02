package de.hsh.favs.sharewise.settlement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRecorded;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentRejected;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import de.hsh.favs.sharewise.settlement.domain.PaymentStatus;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API contract of the settlement payment endpoints (record, history, confirm, reject)
 * with real accounts. Bob owes Alice 90.00 in the group of each test.
 */
@ModuleIntegrationTest
class SettlementPaymentControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	GroupLedgerService ledgers;

	@Autowired
	Balances balances;

	@Autowired
	SettlementPaymentRepository payments;

	@Autowired
	ApplicationEvents events;

	@Autowired
	MutableClock clock;

	SettlementRequests settlement;

	AccountFixture alice;

	AccountFixture bob;

	AccountFixture carol;

	UUID group;

	@BeforeEach
	void bobOwesAlice90() throws Exception {
		clock.reset();
		settlement = new SettlementRequests(mvc, json);
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		carol = AccountFixture.signUp(mvc, json, "Carol");
		group = new GroupRequests(mvc, json).groupWith(alice, "Trip", bob, carol);
		SettlementRequests.bookDebt(ledgers, group, alice, bob, "90.00");
	}

	@Test
	void recordReturns201PendingWithTheFieldsOfTheContract() throws Exception {
		// when / then
		settlement.record(group, bob, alice, "40.00", "Bank transfer")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.groupId").value(group.toString()))
			.andExpect(jsonPath("$.fromDebtor").value(bob.idString()))
			.andExpect(jsonPath("$.fromDebtorName").value(bob.displayName()))
			.andExpect(jsonPath("$.toCreditor").value(alice.idString()))
			.andExpect(jsonPath("$.toCreditorName").value(alice.displayName()))
			.andExpect(jsonPath("$.amount").value("40.00"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.note").value("Bank transfer"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.resolvedAt").value(nullValue()))
			.andExpect(jsonPath("$.length()").value(11));
		assertThat(events.stream(SettlementPaymentRecorded.class).count()).isEqualTo(1);
	}

	@Test
	void recordWithoutANoteHasANullNote() throws Exception {
		settlement.record(group, bob, alice, "10.00", null)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.note").value(nullValue()));
	}

	@Test
	void recordAboveTheEdgeIs422PaymentExceedsOpenDebt() throws Exception {
		settlement.record(group, bob, alice, "90.01", null)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_OPEN_DEBT"));
	}

	@Test
	void aZeroOrNegativeAmountIs422AmountNotPositive() throws Exception {
		settlement.record(group, bob, alice, "0.00", null)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"));
		settlement.record(group, bob, alice, "-5.00", null)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"));
	}

	@Test
	void aSecondRecordForTheSameEdgeIsRefusedWhileTheFirstIsPending() throws Exception {
		// given the first payment reserves the whole debt
		settlement.recordedPayment(group, bob, alice, "90.00");

		// when / then
		settlement.record(group, bob, alice, "0.01", null)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_OPEN_DEBT"));
	}

	@Test
	void recordToSomeoneOutsideTheGroupIs422() throws Exception {
		AccountFixture outsider = AccountFixture.signUp(mvc, json, "Outsider");

		settlement.record(group, bob, outsider, "10.00", null)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_IN_GROUP"));
	}

	@Test
	void amountWithThreeDecimalsOrExponentOrStringIs400() throws Exception {
		String creditor = alice.idString();
		for (String amount : List.of("\"10.001\"", "\"1e1\"", "\"abc\"", "\"\"", "10", "10.5", "null", "1e1", "[]")) {
			settlement.recordRaw(group, bob, "{\"toCreditor\":\"" + creditor + "\",\"amount\":" + amount + "}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		}
		settlement.recordRaw(group, bob, "{\"amount\":\"10.00\"}").andExpect(status().isBadRequest());
		settlement.recordRaw(group, bob, "{\"toCreditor\":\"" + creditor + "\"}").andExpect(status().isBadRequest());
		settlement.recordRaw(group, bob, "{\"toCreditor\":\"not-a-uuid\",\"amount\":\"10.00\"}")
			.andExpect(status().isBadRequest());
		settlement.recordRaw(group, bob, "{").andExpect(status().isBadRequest());
	}

	@Test
	void aTooLongNoteIs400() throws Exception {
		settlement.record(group, bob, alice, "10.00", "x".repeat(501))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void nonMemberRecordIs403() throws Exception {
		AccountFixture outsider = AccountFixture.signUp(mvc, json, "Outsider");

		settlement.record(group, outsider, alice, "10.00", null)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void unknownGroupIs404() throws Exception {
		settlement.record(UUID.randomUUID(), bob, alice, "10.00", null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
		settlement.history(UUID.randomUUID(), bob)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void noTokenIs401() throws Exception {
		UUID payment = settlement.recordedPayment(group, bob, alice, "10.00");

		mvc.perform(
				post("/groups/{id}/settlement-payments", group).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(post("/settlement-payments/{id}/confirm", payment)).andExpect(status().isUnauthorized());
		mvc.perform(post("/settlement-payments/{id}/reject", payment).contentType(MediaType.APPLICATION_JSON)
			.content("{\"note\":\"x\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void confirmByTheCreditorIs200AndBalancesFollow() throws Exception {
		// given
		UUID payment = settlement.recordedPayment(group, bob, alice, "90.00");

		// when
		settlement.confirm(payment, alice)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(payment.toString()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.resolvedAt").isNotEmpty());

		// then the ledger books the payment from the event and everybody is at zero
		assertThat(events.stream(SettlementPaymentConfirmed.class).count()).isEqualTo(1);
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balancesOf(group)).allMatch(balance -> balance.netBalance().isZero()));
		settlement.plan(group, alice).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void confirmByTheDebtorIs403NotTheCreditor() throws Exception {
		UUID payment = settlement.recordedPayment(group, bob, alice, "10.00");

		settlement.confirm(payment, bob)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_THE_CREDITOR"));
		settlement.confirm(payment, carol)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_THE_CREDITOR"));
	}

	@Test
	void confirmByANonMemberIs404SettlementPaymentNotFound() throws Exception {
		UUID payment = settlement.recordedPayment(group, bob, alice, "10.00");
		AccountFixture outsider = AccountFixture.signUp(mvc, json, "Outsider");

		settlement.confirm(payment, outsider)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SETTLEMENT_PAYMENT_NOT_FOUND"));
		settlement.confirm(UUID.randomUUID(), alice)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SETTLEMENT_PAYMENT_NOT_FOUND"));
	}

	@Test
	void confirmWithAPaymentIdOfAnotherGroupIs404() throws Exception {
		// given the payment lives in a group that carol is not in
		UUID other = new GroupRequests(mvc, json).groupWith(alice, "Other", bob);
		SettlementRequests.bookDebt(ledgers, other, alice, bob, "20.00");
		UUID payment = settlement.recordedPayment(other, bob, alice, "20.00");

		// when / then carol learns nothing about it
		settlement.confirm(payment, carol)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SETTLEMENT_PAYMENT_NOT_FOUND"));
	}

	@Test
	void confirmTwiceIs409PaymentNotPending() throws Exception {
		UUID payment = settlement.recordedPayment(group, bob, alice, "10.00");
		settlement.confirm(payment, alice).andExpect(status().isOk());

		settlement.confirm(payment, alice)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYMENT_NOT_PENDING"));
		settlement.reject(payment, alice, "too late")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYMENT_NOT_PENDING"));
	}

	@Test
	void rejectWithTheReasonIs200AndReleasesTheReservation() throws Exception {
		UUID payment = settlement.recordedPayment(group, bob, alice, "90.00");

		settlement.reject(payment, alice, "Money has not arrived")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.note").value("Money has not arrived"))
			.andExpect(jsonPath("$.resolvedAt").isNotEmpty());

		assertThat(events.stream(SettlementPaymentRejected.class).count()).isEqualTo(1);
		settlement.recordedPayment(group, bob, alice, "90.00");
	}

	@Test
	void rejectWithoutANoteIs400() throws Exception {
		UUID payment = settlement.recordedPayment(group, bob, alice, "10.00");

		settlement.reject(payment, alice, null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		settlement.reject(payment, alice, "   ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		settlement.reject(payment, alice, "x".repeat(501)).andExpect(status().isBadRequest());
	}

	@Test
	void rejectByTheDebtorIs403() throws Exception {
		UUID payment = settlement.recordedPayment(group, bob, alice, "10.00");

		settlement.reject(payment, bob, "no")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_THE_CREDITOR"));
	}

	@Test
	void historyListsPaymentsNewestFirstForMembersOnly() throws Exception {
		// given two payments recorded a minute apart
		UUID first = settlement.recordedPayment(group, bob, alice, "10.00");
		clock.advance(Duration.ofMinutes(1));
		UUID second = settlement.recordedPayment(group, bob, alice, "20.00");
		settlement.confirm(first, alice).andExpect(status().isOk());

		// when / then
		JsonNode list = json.readTree(settlement.body(settlement.history(group, carol).andExpect(status().isOk())));
		assertThat(list.size()).isEqualTo(2);
		assertThat(list.get(0).get("id").asString()).isEqualTo(second.toString());
		assertThat(list.get(1).get("id").asString()).isEqualTo(first.toString());
		assertThat(list.get(1).get("status").asString()).isEqualTo("CONFIRMED");
		AccountFixture outsider = AccountFixture.signUp(mvc, json, "Outsider");
		settlement.history(group, outsider)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void everyRefusedCallLeavesNoRowNoEventNoLedgerEntry() throws Exception {
		// given one pending payment of 10.00
		UUID payment = settlement.recordedPayment(group, bob, alice, "10.00");
		AccountFixture outsider = AccountFixture.signUp(mvc, json, "Outsider");
		long recordedBefore = events.stream(SettlementPaymentRecorded.class).count();
		List<MemberBalance> balancesBefore = balancesOf(group);
		int rowsBefore = payments.findByGroupIdOrderByCreatedAtDesc(group).size();

		// when every kind of refusal is tried
		settlement.record(group, bob, alice, "90.00", null).andExpect(status().isUnprocessableContent());
		settlement.record(group, bob, alice, "0.00", null).andExpect(status().isUnprocessableContent());
		settlement.recordRaw(group, bob, "{\"toCreditor\":\"" + alice.idString() + "\",\"amount\":\"1e1\"}")
			.andExpect(status().isBadRequest());
		settlement.record(group, outsider, alice, "1.00", null).andExpect(status().isForbidden());
		settlement.record(group, bob, outsider, "1.00", null).andExpect(status().isUnprocessableContent());
		settlement.confirm(payment, bob).andExpect(status().isForbidden());
		settlement.confirm(payment, outsider).andExpect(status().isNotFound());
		settlement.reject(payment, alice, null).andExpect(status().isBadRequest());
		settlement.waive(group, bob, alice, "1.00", "no").andExpect(status().isUnprocessableContent());

		// then nothing was stored, published or booked
		assertThat(payments.findByGroupIdOrderByCreatedAtDesc(group)).hasSize(rowsBefore);
		assertThat(payments.findById(payment).orElseThrow().status()).isEqualTo(PaymentStatus.PENDING);
		assertThat(events.stream(SettlementPaymentRecorded.class).count()).isEqualTo(recordedBefore);
		assertThat(events.stream(SettlementPaymentConfirmed.class).count()).isZero();
		assertThat(events.stream(SettlementPaymentRejected.class).count()).isZero();
		assertThat(events.stream(DebtWaived.class).count()).isZero();
		assertThat(balancesOf(group)).containsExactlyInAnyOrderElementsOf(balancesBefore);
	}

	private List<MemberBalance> balancesOf(UUID groupId) {
		return balances.of(new GroupId(groupId));
	}

}
