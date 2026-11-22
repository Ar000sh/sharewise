package de.hsh.favs.sharewise.group;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.group.application.GroupArchiveService;
import de.hsh.favs.sharewise.expense.web.ExpenseRequests;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.application.*;
import de.hsh.favs.sharewise.shared.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.json.JsonMapper;

@ModuleIntegrationTest
class GroupLifecycleIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	GroupArchiveService archives;

	@Autowired
	SettlementPaymentService payments;

	@Autowired
	Balances balances;

	AccountFixture alice, bob, outsider;

	UUID group;

	@BeforeEach
	void setup() throws Exception {
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		outsider = AccountFixture.signUp(mvc, json, "Outsider");
		group = new GroupRequests(mvc, json).groupWith(alice, "Archive trip", bob);
	}

	ResultActions command(String action, AccountFixture actor, long revision, String reason) throws Exception {
		return mvc.perform(post("/groups/" + group + "/" + action).header("Authorization", actor.bearer())
			.contentType(APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("expectedRevision", revision, "reason", reason))));
	}

	@Test
	void permissionsReasonsAndClosedWriteGuards() throws Exception {
		mvc.perform(post("/groups/" + group + "/close").contentType(APPLICATION_JSON)
			.content("{\"expectedRevision\":0,\"reason\":\"\"}")).andExpect(status().isUnauthorized());
		command("close", bob, 0, "").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_AN_ADMIN"));
		command("close", alice, 0, "").andExpect(status().isOk()).andExpect(jsonPath("$.archiveCount").value(1));
		new ExpenseRequests(mvc, json).inGroup(group)
			.splitBetween(alice, bob)
			.record(alice)
			.andExpect(status().isUnprocessableContent());
		command("reopen", bob, 1, "Fix").andExpect(status().isForbidden());
		command("reopen", alice, 1, " ").andExpect(status().isBadRequest());
		command("close", alice, 0, "").andExpect(status().isConflict());
		mvc.perform(get("/groups/" + group + "/archives").header("Authorization", outsider.bearer()))
			.andExpect(status().isForbidden());
		command("reopen", alice, 1, "Correct receipt").andExpect(status().isOk());
		command("close", alice, 0, "").andExpect(status().isConflict());
		command("close", alice, 2, "").andExpect(status().isOk()).andExpect(jsonPath("$.archiveCount").value(2));
	}

	@Test
	void correctionAndRepaymentCreateANewSnapshotWithoutChangingTheOldOne() throws Exception {
		var id = new GroupId(group);
		String body = new ExpenseRequests(mvc, json).inGroup(group)
			.withTotalAmount("100.00")
			.splitBetween(alice, bob)
			.record(alice)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String expense = json.readTree(body).get("id").asString();
		waitForBalance(bob.userId(), "-50.00");
		command("close", alice, 0, "").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("BALANCES_NOT_ZERO"));
		var p = payments
			.record(new RecordSettlementPaymentCommand(id, bob.userId(), alice.userId(), Money.eur("50.00"), null));
		command("close", alice, 0, "").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PAYMENTS_PENDING"));
		payments.confirm(alice.userId(), p.paymentId());
		waitForBalance(bob.userId(), "0.00");
		assertThat(archives.getLifecycle(id, alice.userId()).status().name()).isEqualTo("OPEN");
		command("close", alice, 0, "").andExpect(status().isOk());
		String original = json.writeValueAsString(archives.listArchives(id, alice.userId()).getFirst());
		command("reopen", alice, 1, "Wrong receipt amount").andExpect(status().isOk());
		mvc.perform(
				post("/groups/" + group + "/expenses/" + expense + "/correct").header("Authorization", alice.bearer())
					.contentType(APPLICATION_JSON)
					.content("{\"title\":\"Corrected hotel\",\"totalAmount\":\"80.00\"}"))
			.andExpect(status().isOk());
		waitForBalance(bob.userId(), "10.00");
		var refund = payments
			.record(new RecordSettlementPaymentCommand(id, alice.userId(), bob.userId(), Money.eur("10.00"), null));
		payments.confirm(bob.userId(), refund.paymentId());
		waitForBalance(bob.userId(), "0.00");
		command("close", alice, 2, "").andExpect(status().isOk());
		var versions = archives.listArchives(id, alice.userId());
		assertThat(versions).hasSize(2);
		assertThat(json.writeValueAsString(versions.getFirst())).isEqualTo(original);
		assertThat(versions.getLast().snapshot().finances().expenses()).hasSize(2);
		assertThat(versions.getLast().snapshot().finances().payments()).hasSize(2);
	}

	@Test
	void concurrentCloseCreatesOneVersion() throws Exception {
		var pool = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			Callable<Boolean> close = () -> {
				start.await();
				try {
					archives.closeGroup(new GroupId(group), alice.userId(), 0);
					return true;
				}
				catch (DomainException e) {
					assertThat(e.code()).isEqualTo(ErrorCode.LIFECYCLE_CONFLICT);
					return false;
				}
			};
			var a = pool.submit(close);
			var b = pool.submit(close);
			start.countDown();
			assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
			assertThat(archives.listArchives(new GroupId(group), alice.userId())).hasSize(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	void waitForBalance(UserId person, String expected) {
		await().atMost(Duration.ofSeconds(15))
			.untilAsserted(() -> assertThat(balances.of(new GroupId(group))
				.stream()
				.filter(b -> b.userId().equals(person))
				.findFirst()
				.orElseThrow()
				.netBalance()
				.amount()).isEqualByComparingTo(expected));
	}

}
