package de.hsh.favs.sharewise.expense.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HTTP set-up for the expense endpoints. Defaults: "Hotel", 10.00 EUR on 2027-06-01, EQUAL, paid by the first participant. */
class ExpenseRequests {

	private final MockMvc mvc;
	private Object groupId = UUID.randomUUID();
	private String title = "Hotel";
	private long amountCents = 1_000;
	private String date = "2027-06-01";
	private String payer;
	private List<String> participants = List.of();
	private String split = "EQUAL";

	ExpenseRequests(MockMvc mvc) {
		this.mvc = mvc;
	}

	ExpenseRequests inGroup(Object groupId) {
		this.groupId = groupId;
		return this;
	}

	ExpenseRequests withTitle(String title) {
		this.title = title;
		return this;
	}

	ExpenseRequests withAmountCents(long amountCents) {
		this.amountCents = amountCents;
		return this;
	}

	ExpenseRequests onDate(String date) {
		this.date = date;
		return this;
	}

	ExpenseRequests paidBy(Object payer) {
		this.payer = payer.toString();
		return this;
	}

	ExpenseRequests splitBetween(Object... participants) {
		this.participants = java.util.Arrays.stream(participants).map(Object::toString).toList();
		return this;
	}

	ExpenseRequests withSplit(String split) {
		this.split = split;
		return this;
	}

	ResultActions record() throws Exception {
		String payerId = payer != null ? payer : participants.get(0);
		String body = """
				{"title":"%s","amountCents":%d,"date":"%s","payerId":"%s","participantIds":["%s"],"split":"%s"}"""
				.formatted(title, amountCents, date, payerId, String.join("\",\"", participants), split);
		return mvc.perform(post("/groups/{id}/expenses", groupId).contentType(APPLICATION_JSON).content(body));
	}

	ResultActions list() throws Exception {
		return mvc.perform(get("/groups/{id}/expenses", groupId));
	}

}
