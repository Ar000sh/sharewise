package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test data for a user. Defaults to Alice; {@link #bob()}, {@link #carol()} and
 * {@link #david()} are the others. These have fixed ids and only exist in memory, which
 * is enough for unit tests. Integration tests call
 * {@link #signUp(MockMvc, JsonMapper, String)} to get a real account with a session
 * token. Override anything with {@code withX}.
 */
public class UserFixture {

	private UUID id = seededId(1);

	private String displayName = "Alice";

	private String token;

	public static UserFixture alice() {
		return new UserFixture();
	}

	public static UserFixture bob() {
		return new UserFixture().withId(seededId(2)).withDisplayName("Bob");
	}

	public static UserFixture carol() {
		return new UserFixture().withId(seededId(3)).withDisplayName("Carol");
	}

	public static UserFixture david() {
		return new UserFixture().withId(seededId(4)).withDisplayName("David");
	}

	/** A well-formed id that is not in the users table. */
	public static UUID unknownId() {
		return seededId(255);
	}

	/**
	 * Signs a new user up through {@code POST /auth/register} (unique email) and returns
	 * it with id and token.
	 */
	public static UserFixture signUp(MockMvc mvc, JsonMapper json, String displayName) throws Exception {
		String body = json
			.writeValueAsString(Map.of("email", displayName.toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
					"displayName", displayName, "password", "secret-pass"));
		var created = json.readTree(
				mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body)).andReturn().getResponse().getContentAsString());
		return new UserFixture().withId(UUID.fromString(created.get("userId").asString()))
			.withDisplayName(displayName)
			.withToken(created.get("token").asString());
	}

	public UserFixture withToken(String token) {
		this.token = token;
		return this;
	}

	/** Value of the Authorization header. */
	public String bearer() {
		return "Bearer " + token;
	}

	public UserFixture withId(UUID id) {
		this.id = id;
		return this;
	}

	public UserFixture withDisplayName(String displayName) {
		this.displayName = displayName;
		return this;
	}

	public UUID id() {
		return id;
	}

	public String idString() {
		return id.toString();
	}

	public String displayName() {
		return displayName;
	}

	public UserId userId() {
		return new UserId(id);
	}

	public MemberId memberId() {
		return new MemberId(id);
	}

	public UserInfo info() {
		return new UserInfo(userId(), displayName);
	}

	User build() {
		return new User(id, displayName);
	}

	private static UUID seededId(int n) {
		return UUID.fromString("aaaaaaaa-0000-0000-0000-%012d".formatted(n));
	}

}
