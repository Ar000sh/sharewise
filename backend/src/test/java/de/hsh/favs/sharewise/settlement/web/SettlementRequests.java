package de.hsh.favs.sharewise.settlement.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP calls for the settlement endpoints as a given user, so controller tests set up
 * state in one line. A {@code null} note is left out of the body.
 */
public class SettlementRequests {

	private static final String AUTHORIZATION = "Authorization";

	private final MockMvc mvc;

	private final JsonMapper json;

	public SettlementRequests(MockMvc mvc, JsonMapper json) {
		this.mvc = mvc;
		this.json = json;
	}

	public ResultActions plan(UUID group, AccountFixture caller) throws Exception {
		return mvc.perform(get("/groups/{id}/settlement-plan", group).header(AUTHORIZATION, caller.bearer()));
	}

	public ResultActions record(UUID group, AccountFixture debtor, AccountFixture creditor, String amount, String note)
			throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toCreditor", creditor.idString());
		body.put("amount", amount);
		if (note != null) {
			body.put("note", note);
		}
		return recordRaw(group, debtor, json.writeValueAsString(body));
	}

	/** Records a payment (expects 201) and returns its id. */
	public UUID recordedPayment(UUID group, AccountFixture debtor, AccountFixture creditor, String amount)
			throws Exception {
		return idOf(record(group, debtor, creditor, amount, "Bank transfer").andExpect(status().isCreated()));
	}

	/** The body is sent as given, for malformed amounts. */
	public ResultActions recordRaw(UUID group, AccountFixture debtor, String body) throws Exception {
		return mvc.perform(post("/groups/{id}/settlement-payments", group).header(AUTHORIZATION, debtor.bearer())
			.contentType(APPLICATION_JSON)
			.content(body));
	}

	public ResultActions history(UUID group, AccountFixture caller) throws Exception {
		return mvc.perform(get("/groups/{id}/settlement-payments", group).header(AUTHORIZATION, caller.bearer()));
	}

	public ResultActions confirm(UUID payment, AccountFixture caller) throws Exception {
		return mvc.perform(post("/settlement-payments/{id}/confirm", payment).header(AUTHORIZATION, caller.bearer()));
	}

	public ResultActions reject(UUID payment, AccountFixture caller, String note) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		if (note != null) {
			body.put("note", note);
		}
		return mvc.perform(post("/settlement-payments/{id}/reject", payment).header(AUTHORIZATION, caller.bearer())
			.contentType(APPLICATION_JSON)
			.content(json.writeValueAsString(body)));
	}

	public ResultActions waive(UUID group, AccountFixture creditor, AccountFixture debtor, String amount, String note)
			throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("fromDebtor", debtor.idString());
		body.put("amount", amount);
		if (note != null) {
			body.put("note", note);
		}
		return mvc.perform(post("/groups/{id}/debt-waivers", group).header(AUTHORIZATION, creditor.bearer())
			.contentType(APPLICATION_JSON)
			.content(json.writeValueAsString(body)));
	}

	/**
	 * {@code GET /me/settlement-payments} with a raw query string such as "role=debtor".
	 */
	public ResultActions mine(AccountFixture caller, String query) throws Exception {
		return mvc.perform(get("/me/settlement-payments" + (query.isEmpty() ? "" : "?" + query)).header(AUTHORIZATION,
				caller.bearer()));
	}

	/**
	 * Books, without waiting for an event, an expense after which {@code debtor} owes
	 * {@code creditor} {@code amount}: the creditor paid it and only the debtor shares
	 * it.
	 */
	public static void bookDebt(GroupLedgerService ledgers, UUID group, AccountFixture creditor, AccountFixture debtor,
			String amount) {
		ledgers.bookExpense(new ExpenseRecordedFixture().inGroup(new GroupId(group))
			.paidBy(creditor.userId())
			.withTotalAmount(amount)
			.splitBetween(debtor.userId())
			.build());
	}

	public String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	public UUID idOf(ResultActions result) throws Exception {
		return UUID.fromString(json.readTree(body(result)).get("id").asString());
	}

}
