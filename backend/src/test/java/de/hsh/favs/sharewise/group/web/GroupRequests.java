package de.hsh.favs.sharewise.group.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.account.domain.AccountFixture;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** The group HTTP calls as a given user, so controller tests set up state in one line. */
public class GroupRequests {

	private static final String USER_HEADER = "Authorization";

	private final MockMvc mvc;

	private final JsonMapper json;

	public GroupRequests(MockMvc mvc, JsonMapper json) {
		this.mvc = mvc;
		this.json = json;
	}

	public ResultActions create(AccountFixture caller, String name) throws Exception {
		return mvc.perform(post("/groups").header(USER_HEADER, caller.bearer())
			.contentType(APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("name", name))));
	}

	/** Creates a group (expects 201) and returns its id. */
	public UUID createGroup(AccountFixture creator, String name) throws Exception {
		String body = body(create(creator, name).andExpect(status().isCreated()));
		return UUID.fromString(json.readTree(body).get("id").asString());
	}

	/**
	 * A group created by {@code admin} that each of {@code joiners} joined through an
	 * invitation.
	 */
	public UUID groupWith(AccountFixture admin, String name, AccountFixture... joiners) throws Exception {
		UUID group = createGroup(admin, name);
		for (AccountFixture joiner : joiners) {
			accept(invite(group, admin), joiner).andExpect(status().isOk());
		}
		return group;
	}

	public ResultActions list(AccountFixture caller) throws Exception {
		return mvc.perform(get("/groups").header(USER_HEADER, caller.bearer()));
	}

	public ResultActions detail(UUID group, AccountFixture caller) throws Exception {
		return mvc.perform(get("/groups/" + group).header(USER_HEADER, caller.bearer()));
	}

	public ResultActions createInvitation(UUID group, AccountFixture caller) throws Exception {
		return mvc.perform(post("/groups/" + group + "/invitations").header(USER_HEADER, caller.bearer()));
	}

	/** Creates an invitation (expects 201) and returns its token. */
	public String invite(UUID group, AccountFixture caller) throws Exception {
		String body = body(createInvitation(group, caller).andExpect(status().isCreated()));
		return json.readTree(body).get("token").asString();
	}

	public ResultActions accept(String token, AccountFixture caller) throws Exception {
		return mvc.perform(post("/invitations/" + token + "/accept").header(USER_HEADER, caller.bearer()));
	}

	public String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

}
