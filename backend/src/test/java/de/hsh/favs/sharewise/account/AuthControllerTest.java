package de.hsh.favs.sharewise.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.group.ModuleIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Sign up and sign in through the whole stack down to Postgres. */
@ModuleIntegrationTest
class AuthControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void registeredUserIsSignedInAndKnownToMe() throws Exception {
		// given
		String email = uniqueEmail();

		// when
		ResultActions registered = register(email, "Dana", "secret-pass").andExpect(status().isCreated())
			.andExpect(jsonPath("$.displayName").value("Dana"));
		String token = field(registered, "token");

		// then the token is a bearer token for /me
		mvc.perform(get("/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(field(registered, "userId")))
			.andExpect(jsonPath("$.displayName").value("Dana"));
	}

	@Test
	void passwordIsStoredHashedAndTokenIsNotStoredAtAll() throws Exception {
		// given / when
		String email = uniqueEmail();
		String token = field(register(email, "Erin", "secret-pass"), "token");

		// then
		String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
		assertThat(hash).isNotEqualTo("secret-pass").startsWith("$2");
		Integer rawTokens = jdbc.queryForObject("select count(*) from auth_session where token_hash = ?", Integer.class,
				token);
		assertThat(rawTokens).isZero();
	}

	@Test
	void loginWithTheRightPasswordWorksAndEmailIsCaseInsensitive() throws Exception {
		// given
		String email = uniqueEmail();
		register(email, "Finn", "secret-pass");

		// when / then
		String token = field(login(email.toUpperCase(), "secret-pass").andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Finn")), "token");
		mvc.perform(get("/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
		// given
		String email = uniqueEmail();
		register(email, "Gina", "secret-pass");

		// then
		login(email, "wrong-pass").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		login(uniqueEmail(), "secret-pass").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void registeringTheSameEmailTwiceIsAConflict() throws Exception {
		// given
		String email = uniqueEmail();
		register(email, "Hana", "secret-pass").andExpect(status().isCreated());

		// when / then
		register(email.toUpperCase(), "Hana 2", "other-pass1").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void invalidSignUpIsABadRequest() throws Exception {
		register("not-an-email", "Ida", "secret-pass").andExpect(status().isBadRequest());
		register(uniqueEmail(), "", "secret-pass").andExpect(status().isBadRequest());
		register(uniqueEmail(), "Ida", "short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void unknownOrMalformedBearerTokenIs401() throws Exception {
		mvc.perform(get("/me").header("Authorization", "Bearer nope"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(get("/groups").header("Authorization", "Basic abc")).andExpect(status().isUnauthorized());
	}

	// --- helpers ---

	private ResultActions register(String email, String displayName, String password) throws Exception {
		return send("/auth/register", Map.of("email", email, "displayName", displayName, "password", password));
	}

	private ResultActions login(String email, String password) throws Exception {
		return send("/auth/login", Map.of("email", email, "password", password));
	}

	private ResultActions send(String path, Map<String, String> body) throws Exception {
		return mvc.perform(post(path).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private String field(ResultActions result, String name) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString()).get(name).asString();
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
