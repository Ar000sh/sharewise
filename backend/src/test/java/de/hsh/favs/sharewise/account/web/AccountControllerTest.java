package de.hsh.favs.sharewise.account.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * API contract of /me with real Keycloak tokens, through the whole stack down to
 * Postgres.
 */
@ModuleIntegrationTest
class AccountControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void meReturnsTheProfileOfTheTokensAccount() throws Exception {
		// given
		AccountFixture dana = AccountFixture.signUp(mvc, json, "Dana");

		// when / then
		mvc.perform(get("/me").header("Authorization", dana.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(dana.idString()))
			// the token's name claim: first name plus the test user's last name (C7)
			.andExpect(jsonPath("$.displayName").value("Dana Tester"));
	}

	@Test
	void meReturnsTheUsername() throws Exception {
		// given
		AccountFixture jana = AccountFixture.signUp(mvc, json, "Jana");

		// when / then
		mvc.perform(get("/me").header("Authorization", jana.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(jana.username()));
	}

	@Test
	void meWithoutATokenIs401Unauthenticated() throws Exception {
		mvc.perform(get("/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.status").value(401));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bearer not-a-token", "Bearer ", "Basic abc" })
	void anInvalidAuthorizationHeaderIs401(String header) throws Exception {
		mvc.perform(get("/me").header("Authorization", header))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void aUserIdInTheOldStubHeaderProvesNothing() throws Exception {
		// given
		AccountFixture carol = AccountFixture.signUp(mvc, json, "Carol");

		// when / then
		mvc.perform(get("/me").header("X-User-Id", carol.idString())).andExpect(status().isUnauthorized());
	}

	@Test
	void actuatorNeedsNoCredentials() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
