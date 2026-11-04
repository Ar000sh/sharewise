package de.hsh.favs.sharewise;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

/**
 * Creates real users in the test Keycloak and hands out real access tokens for them.
 *
 * <p>
 * The realm file has no client with direct access grants on purpose (no back door in the
 * file that also runs in compose). So the first call creates such a client, once, through
 * the admin API of the test container; it never exists anywhere else (design C1). The
 * base URL is set by {@link TestcontainersConfiguration} when the container is up, which
 * keeps this class usable from plain static helpers such as a fixture.
 */
public final class KeycloakUsers {

	/** What a test needs to act as the created user. */
	public record Login(String subject, String email, String username, String accessToken) {
	}

	static final String ADMIN_USER = "admin";

	static final String ADMIN_PASSWORD = "admin";

	private static final String REALM = "sharewise";

	private static final String TEST_CLIENT = "sharewise-test";

	private static final String PASSWORD = "Test-pass-1234";

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static String baseUrl;

	private static boolean clientCreated;

	private KeycloakUsers() {
	}

	static synchronized void configure(String keycloakBaseUrl) {
		baseUrl = keycloakBaseUrl;
		clientCreated = false;
	}

	/**
	 * Registers a new user with a unique email and a unique valid username, the given
	 * first name and a fixed last name, and signs them in.
	 */
	public static synchronized Login create(String displayName) {
		String letters = displayName.toLowerCase().replaceAll("[^a-z0-9]", "");
		String username = letters.substring(0, Math.min(letters.length(), 12)) + "-"
				+ UUID.randomUUID().toString().substring(0, 8);
		return create(displayName, username);
	}

	/**
	 * Like {@link #create(String)} with the given username. Throws when Keycloak refuses
	 * it, which is how the realm's username rule is tested.
	 */
	public static synchronized Login create(String displayName, String username) {
		if (baseUrl == null) {
			throw new IllegalStateException("Keycloak test container is not started; use @ModuleIntegrationTest");
		}
		String adminToken = adminToken();
		ensureDirectGrantClient(adminToken);
		String email = displayName.toLowerCase().replaceAll("[^a-z0-9]", "") + "-" + UUID.randomUUID()
				+ "@example.test";
		Map<String, Object> user = Map.of("username", username, "email", email, "firstName", displayName, "lastName",
				"Tester", "enabled", true, "emailVerified", true, "credentials",
				List.of(Map.of("type", "password", "value", PASSWORD, "temporary", false)));
		HttpResponse<String> created = send(HttpRequest.newBuilder(uri("/admin/realms/" + REALM + "/users"))
			.header("Authorization", "Bearer " + adminToken)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(user))));
		require(created, 201, "create user");
		String location = created.headers().firstValue("Location").orElseThrow();
		String subject = location.substring(location.lastIndexOf('/') + 1);
		return new Login(subject, email, username.toLowerCase(), accessToken(username));
	}

	private static String adminToken() {
		HttpResponse<String> response = form("/realms/master/protocol/openid-connect/token", Map.of("client_id",
				"admin-cli", "grant_type", "password", "username", ADMIN_USER, "password", ADMIN_PASSWORD));
		require(response, 200, "admin token");
		return JSON.readTree(response.body()).path("access_token").asString();
	}

	private static void ensureDirectGrantClient(String adminToken) {
		if (clientCreated) {
			return;
		}
		Map<String, Object> client = Map.of("clientId", TEST_CLIENT, "enabled", true, "publicClient", true,
				"directAccessGrantsEnabled", true, "standardFlowEnabled", false);
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/admin/realms/" + REALM + "/clients"))
			.header("Authorization", "Bearer " + adminToken)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(client))));
		if (response.statusCode() != 409) {
			require(response, 201, "create test client");
		}
		clientCreated = true;
	}

	private static String accessToken(String username) {
		HttpResponse<String> response = form("/realms/" + REALM + "/protocol/openid-connect/token", Map.of("client_id",
				TEST_CLIENT, "grant_type", "password", "username", username, "password", PASSWORD, "scope", "openid"));
		require(response, 200, "user token");
		return JSON.readTree(response.body()).path("access_token").asString();
	}

	private static HttpResponse<String> form(String path, Map<String, String> fields) {
		StringBuilder body = new StringBuilder();
		fields.forEach((key, value) -> {
			if (!body.isEmpty()) {
				body.append('&');
			}
			body.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
				.append('=')
				.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
		});
		return send(HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString())));
	}

	private static HttpResponse<String> send(HttpRequest.Builder request) {
		try {
			return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Keycloak request failed", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while calling Keycloak", ex);
		}
	}

	private static void require(HttpResponse<String> response, int expected, String what) {
		if (response.statusCode() != expected) {
			throw new IllegalStateException(
					"Keycloak " + what + " answered " + response.statusCode() + ": " + response.body());
		}
	}

	private static URI uri(String path) {
		return URI.create(baseUrl + path);
	}

}
