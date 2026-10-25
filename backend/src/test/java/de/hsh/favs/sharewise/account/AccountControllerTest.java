package de.hsh.favs.sharewise.account;

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
import tools.jackson.databind.json.JsonMapper;

/** API contract of /me through the whole stack, with a user who really signed up. */
@ModuleIntegrationTest
class AccountControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void meReturnsTheSignedInUser() throws Exception {
		// given
		UserFixture bob = UserFixture.signUp(mvc, json, "Bob");

		// when / then
		mvc.perform(get("/me").header("Authorization", bob.bearer())).andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(bob.idString()))
				.andExpect(jsonPath("$.displayName").value("Bob"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bearer not-a-token", "Bearer ", "Basic abc", "aaaaaaaa-0000-0000-0000-000000000001" })
	void meWithAnInvalidAuthorizationHeaderIs401(String header) throws Exception {
		mvc.perform(get("/me").header("Authorization", header)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void meWithoutCredentialsIs401ProblemJson() throws Exception {
		mvc.perform(get("/me")).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void theOldStubHeaderProvesNothing() throws Exception {
		// given
		UserFixture carol = UserFixture.signUp(mvc, json, "Carol");

		// when / then
		mvc.perform(get("/me").header("X-User-Id", carol.idString())).andExpect(status().isUnauthorized());
	}

	@Test
	void actuatorNeedsNoCredentials() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
