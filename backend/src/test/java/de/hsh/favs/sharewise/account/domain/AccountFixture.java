package de.hsh.favs.sharewise.account.domain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import de.hsh.favs.sharewise.KeycloakUsers;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test data for an account. Defaults to Alice; {@link #bob()}, {@link #carol()} and
 * {@link #david()} are the others. They have fixed ids and live in memory, which is
 * enough for unit and service tests. Integration tests call
 * {@link #signUp(MockMvc, JsonMapper, String)} to get a real Keycloak user whose account
 * exists in ShareWise, with a real access token. Override anything with {@code withX}.
 */
public class AccountFixture {

	private static final Instant NOW = Instant.parse("2027-06-01T10:00:00Z");

	private UUID id = seededId(1);

	private String displayName = "Alice";

	private String username = "alice";

	private String email = "alice@example.com";

	private String token;

	public static AccountFixture alice() {
		return new AccountFixture();
	}

	public static AccountFixture bob() {
		return new AccountFixture().withId(seededId(2))
			.withDisplayName("Bob")
			.withUsername("bob")
			.withEmail("bob@example.com");
	}

	public static AccountFixture carol() {
		return new AccountFixture().withId(seededId(3))
			.withDisplayName("Carol")
			.withUsername("carol")
			.withEmail("carol@example.com");
	}

	public static AccountFixture david() {
		return new AccountFixture().withId(seededId(4))
			.withDisplayName("David")
			.withUsername("david")
			.withEmail("david@example.com");
	}

	/** A well-formed id that belongs to no account. */
	public static UUID unknownId() {
		return seededId(255);
	}

	/**
	 * Creates a new user in the test Keycloak (unique email) and calls {@code GET /me}
	 * with its access token, which creates the account the way a first login does (D4).
	 * Returns it with id (the Keycloak subject), email, token and the display name the
	 * account got: the token's {@code name} claim, which is the given first name plus the
	 * test user's last name (C7), so assert against {@link #displayName()}, not the
	 * argument.
	 */
	public static AccountFixture signUp(MockMvc mvc, JsonMapper json, String displayName) throws Exception {
		KeycloakUsers.Login login = KeycloakUsers.create(displayName);
		var me = mvc.perform(get("/me").header("Authorization", "Bearer " + login.accessToken()))
			.andReturn()
			.getResponse();
		if (me.getStatus() != 200) {
			throw new IllegalStateException("GET /me for a new Keycloak user answered " + me.getStatus());
		}
		return new AccountFixture().withId(UUID.fromString(login.subject()))
			.withDisplayName(json.readTree(me.getContentAsString()).get("displayName").asString())
			.withUsername(login.username())
			.withEmail(login.email())
			.withToken(login.accessToken());
	}

	public AccountFixture withId(UUID id) {
		this.id = id;
		return this;
	}

	public AccountFixture withDisplayName(String displayName) {
		this.displayName = displayName;
		return this;
	}

	public AccountFixture withUsername(String username) {
		this.username = username;
		return this;
	}

	public AccountFixture withEmail(String email) {
		this.email = email;
		return this;
	}

	public AccountFixture withToken(String token) {
		this.token = token;
		return this;
	}

	/**
	 * An ACTIVE {@link Account} with this fixture's id, built through the real factory.
	 */
	public Account build() {
		return Account.registerFromIdentityProvider(userId(), new Email(email), new Username(username),
				new DisplayName(displayName), NOW);
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

	public String username() {
		return username;
	}

	public String email() {
		return email;
	}

	/**
	 * Value of the Authorization header; only for accounts created with {@code signUp}.
	 */
	public String bearer() {
		return "Bearer " + token;
	}

	public UserProfile profile() {
		return new UserProfile(userId(), displayName, username, true);
	}

	private static UUID seededId(int n) {
		return UUID.fromString("aaaaaaaa-0000-0000-0000-%012d".formatted(n));
	}

}
