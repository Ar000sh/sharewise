package de.hsh.favs.sharewise.account.domain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test data for an account. Defaults to Alice; {@link #bob()}, {@link #carol()} and
 * {@link #david()} are the others. They have fixed ids and live in memory, which is
 * enough for unit and service tests. Integration tests call
 * {@link #signUp(MockMvc, JsonMapper, String)} to get a real account with a session
 * token. Override anything with {@code withX}.
 */
public class AccountFixture {

	public static final String PASSWORD = "secret-pass";

	private static final Instant NOW = Instant.parse("2027-06-01T10:00:00Z");

	private UUID id = seededId(1);

	private String displayName = "Alice";

	private String email = "alice@example.com";

	private String password = PASSWORD;

	private String token;

	public static AccountFixture alice() {
		return new AccountFixture();
	}

	public static AccountFixture bob() {
		return new AccountFixture().withId(seededId(2)).withDisplayName("Bob").withEmail("bob@example.com");
	}

	public static AccountFixture carol() {
		return new AccountFixture().withId(seededId(3)).withDisplayName("Carol").withEmail("carol@example.com");
	}

	public static AccountFixture david() {
		return new AccountFixture().withId(seededId(4)).withDisplayName("David").withEmail("david@example.com");
	}

	/** A well-formed id that belongs to no account. */
	public static UUID unknownId() {
		return seededId(255);
	}

	/**
	 * Signs a new account up through {@code POST /auth/register} (unique email) and
	 * returns it with id and token.
	 */
	public static AccountFixture signUp(MockMvc mvc, JsonMapper json, String displayName) throws Exception {
		String email = displayName.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
		String body = json.writeValueAsString(Map.of("email", email, "displayName", displayName, "password", PASSWORD));
		var created = json
			.readTree(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn()
				.getResponse()
				.getContentAsString());
		return new AccountFixture().withId(UUID.fromString(created.get("userId").asString()))
			.withDisplayName(displayName)
			.withEmail(email)
			.withToken(created.get("token").asString());
	}

	public AccountFixture withId(UUID id) {
		this.id = id;
		return this;
	}

	public AccountFixture withDisplayName(String displayName) {
		this.displayName = displayName;
		return this;
	}

	public AccountFixture withEmail(String email) {
		this.email = email;
		return this;
	}

	public AccountFixture withPassword(String password) {
		this.password = password;
		return this;
	}

	public AccountFixture withToken(String token) {
		this.token = token;
		return this;
	}

	/**
	 * A registered {@link Account} with this fixture's id, built through the real factory
	 * with a {@link StubHasher}.
	 */
	public Account build() {
		Account account = Account
			.register(new Email(email), new DisplayName(displayName), new RawPassword(password), new StubHasher(), NOW)
			.account();
		ReflectionTestUtils.setField(account, "id", id);
		return account;
	}

	public UUID id() {
		return id;
	}

	public String idString() {
		return id.toString();
	}

	public UserId userId() {
		return new UserId(id);
	}

	public String displayName() {
		return displayName;
	}

	public String email() {
		return email;
	}

	public String password() {
		return password;
	}

	/**
	 * Value of the Authorization header; only for accounts created with {@code signUp}.
	 */
	public String bearer() {
		return "Bearer " + token;
	}

	public UserProfile profile() {
		return new UserProfile(userId(), displayName, true);
	}

	private static UUID seededId(int n) {
		return UUID.fromString("aaaaaaaa-0000-0000-0000-%012d".formatted(n));
	}

}
