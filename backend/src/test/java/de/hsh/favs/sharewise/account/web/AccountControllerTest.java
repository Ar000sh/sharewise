package de.hsh.favs.sharewise.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** API contract of sign-up, sign-in and /me through the whole stack down to Postgres. */
@ModuleIntegrationTest
class AccountControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void aRegisteredUserIsSignedInAndKnownToMe() throws Exception {
		// given
		String email = uniqueEmail();

		// when
		ResultActions registered = register(email, "Dana", "secret-pass").andExpect(status().isCreated())
			.andExpect(jsonPath("$.displayName").value("Dana"));

		// then the token is a bearer token for /me
		mvc.perform(get("/me").header("Authorization", "Bearer " + field(registered, "token")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(field(registered, "userId")))
			.andExpect(jsonPath("$.displayName").value("Dana"));
	}

	@Test
	void thePasswordIsStoredHashedAndTheTokenIsNotStoredAtAll() throws Exception {
		// given / when
		String email = uniqueEmail();
		String token = field(register(email, "Erin", "secret-pass"), "token");

		// then
		String hash = jdbc.queryForObject("select password_hash from accounts where email = ?", String.class, email);
		assertThat(hash).isNotEqualTo("secret-pass").startsWith("$2");
		Integer rawTokens = jdbc.queryForObject("select count(*) from auth_session where token_hash = ?", Integer.class,
				token);
		assertThat(rawTokens).isZero();
	}

	@Test
	void signingInWorksAndTheEmailIsCaseInsensitive() throws Exception {
		// given
		String email = uniqueEmail();
		register(email, "Finn", "secret-pass");

		// when
		String token = field(login(email.toUpperCase(), "secret-pass").andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Finn")), "token");

		// then
		mvc.perform(get("/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
	}

	@Test
	void aWrongPasswordIsCountedAndTheNextRightOneResetsTheCount() throws Exception {
		// given
		String email = uniqueEmail();
		register(email, "Gina", "secret-pass");

		// when / then
		login(email, "wrong-pass").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		assertThat(failedAttempts(email)).isEqualTo(1);
		login(email, "secret-pass").andExpect(status().isOk());
		assertThat(failedAttempts(email)).isZero();
	}

	@Test
	void anUnknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
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
	void invalidSignUpDataIsABadRequest() throws Exception {
		register("not-an-email", "Ida", "secret-pass").andExpect(status().isBadRequest());
		register(uniqueEmail(), "  ", "secret-pass").andExpect(status().isBadRequest());
		register(uniqueEmail(), "Ida", "short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bearer not-a-token", "Bearer ", "Basic abc" })
	void anInvalidAuthorizationHeaderIs401(String header) throws Exception {
		mvc.perform(get("/me").header("Authorization", header))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void withoutCredentialsTheAnswerIs401ProblemJson() throws Exception {
		mvc.perform(get("/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.status").value(401));
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

	private int failedAttempts(String email) {
		return jdbc.queryForObject("select failed_login_attempts from accounts where email = ?", Integer.class,
				email.toLowerCase());
	}

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
