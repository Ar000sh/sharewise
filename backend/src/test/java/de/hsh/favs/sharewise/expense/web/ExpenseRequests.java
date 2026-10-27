package de.hsh.favs.sharewise.expense.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import de.hsh.favs.sharewise.account.domain.AccountFixture;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP calls for the expense endpoints. Defaults: "Hotel", 10.00 EUR on 2027-06-01,
 * EQUAL, paid by the first participant. A field set to {@code null} is left out of the
 * body.
 */
public class ExpenseRequests {

	private final MockMvc mvc;

	private final JsonMapper json;

	private UUID groupId = UUID.randomUUID();

	private String title = "Hotel";

	private String totalAmount = "10.00";

	private String date = "2027-06-01";

	private String paidBy;

	private List<String> participants = List.of();

	private String splitStrategy = "EQUAL";

	public ExpenseRequests(MockMvc mvc, JsonMapper json) {
		this.mvc = mvc;
		this.json = json;
	}

	public ExpenseRequests inGroup(UUID groupId) {
		this.groupId = groupId;
		return this;
	}

	public ExpenseRequests withTitle(String title) {
		this.title = title;
		return this;
	}

	public ExpenseRequests withTotalAmount(String totalAmount) {
		this.totalAmount = totalAmount;
		return this;
	}

	public ExpenseRequests onDate(String date) {
		this.date = date;
		return this;
	}

	public ExpenseRequests paidBy(AccountFixture payer) {
		this.paidBy = payer.idString();
		return this;
	}

	public ExpenseRequests splitBetween(AccountFixture... people) {
		this.participants = Arrays.stream(people).map(AccountFixture::idString).toList();
		return this;
	}

	public ExpenseRequests withSplitStrategy(String splitStrategy) {
		this.splitStrategy = splitStrategy;
		return this;
	}

	public ResultActions record(AccountFixture caller) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("totalAmount", totalAmount);
		body.put("date", date);
		body.put("paidBy", paidBy != null ? paidBy : participants.get(0));
		body.put("participantIds", participants);
		body.put("splitStrategy", splitStrategy);
		return mvc.perform(post("/groups/{id}/expenses", groupId).header("Authorization", caller.bearer())
			.contentType(APPLICATION_JSON)
			.content(json.writeValueAsString(body)));
	}

	public ResultActions list(AccountFixture caller) throws Exception {
		return mvc.perform(get("/groups/{id}/expenses", groupId).header("Authorization", caller.bearer()));
	}

}
