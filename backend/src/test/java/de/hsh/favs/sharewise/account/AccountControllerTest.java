package de.hsh.favs.sharewise.account;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.group.ModuleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** API contract of /me and /users through the whole stack, with the users seeded by the dev migration. */
@ModuleIntegrationTest
class AccountControllerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void meReturnsTheCallerForAKnownHeader() throws Exception {
		// given
		UserFixture bob = UserFixture.bob();

		// when / then
		mvc.perform(get("/me").header("X-User-Id", bob.idString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(bob.idString()))
				.andExpect(jsonPath("$.displayName").value("Bob"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "aaaaaaaa-0000-0000-0000-000000000255" })
	void meWithMalformedOrUnknownHeaderIs401(String header) throws Exception {
		mvc.perform(get("/me").header("X-User-Id", header)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void meWithoutHeaderIs401ProblemJson() throws Exception {
		mvc.perform(get("/me")).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void usersListsTheFourSeededUsersWithoutHeader() throws Exception {
		mvc.perform(get("/users")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].userId").value(UserFixture.alice().idString()))
				.andExpect(jsonPath("$[0].displayName").value("Alice"))
				.andExpect(jsonPath("$[1].displayName").value("Bob"))
				.andExpect(jsonPath("$[2].displayName").value("Carol"))
				.andExpect(jsonPath("$[3].displayName").value("David"));
	}

	@Test
	void actuatorNeedsNoHeader() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
