package de.hsh.favs.sharewise.group;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.account.UserFixture;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** The group HTTP calls as a given user, so controller tests set up state in one line. */
class GroupRequests {

	private static final String USER_HEADER = "Authorization";

	private final MockMvc mvc;

	private final JsonMapper json;

	GroupRequests(MockMvc mvc, JsonMapper json) {
		this.mvc = mvc;
		this.json = json;
	}

	ResultActions create(UserFixture caller, String name) throws Exception {
		return mvc.perform(post("/groups").header(USER_HEADER, caller.bearer()).contentType(APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", name))));
	}

	/** Creates a group (expects 201) and returns its id. */
	UUID createGroup(UserFixture creator, String name) throws Exception {
		String body = body(create(creator, name).andExpect(status().isCreated()));
		return UUID.fromString(json.readTree(body).get("id").asString());
	}

	/** A group created by {@code admin} that each of {@code joiners} joined through an invitation. */
	UUID groupWith(UserFixture admin, String name, UserFixture... joiners) throws Exception {
		UUID group = createGroup(admin, name);
		for (UserFixture joiner : joiners) {
			accept(invite(group, admin), joiner).andExpect(status().isOk());
		}
		return group;
	}

	ResultActions list(UserFixture caller) throws Exception {
		return mvc.perform(get("/groups").header(USER_HEADER, caller.bearer()));
	}

	ResultActions detail(UUID group, UserFixture caller) throws Exception {
		return mvc.perform(get("/groups/" + group).header(USER_HEADER, caller.bearer()));
	}

	ResultActions createInvitation(UUID group, UserFixture caller) throws Exception {
		return mvc.perform(post("/groups/" + group + "/invitations").header(USER_HEADER, caller.bearer()));
	}

	/** Creates an invitation (expects 201) and returns its token. */
	String invite(UUID group, UserFixture caller) throws Exception {
		String body = body(createInvitation(group, caller).andExpect(status().isCreated()));
		return json.readTree(body).get("token").asString();
	}

	ResultActions accept(String token, UserFixture caller) throws Exception {
		return mvc.perform(post("/invitations/" + token + "/accept").header(USER_HEADER, caller.bearer()));
	}

	String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

}
