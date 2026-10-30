package de.hsh.favs.sharewise;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ModuleIntegrationTest
class KeycloakUsersTest {

	@Autowired
	private Environment environment;

	@Autowired
	private JsonMapper json;

	@Test
	void createdUserGetsATokenWithSubEmailAndIssuer() {
		// given
		String issuer = this.environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");

		// when
		KeycloakUsers.Login login = KeycloakUsers.create("Alice");

		// then
		JsonNode claims = payloadOf(login.accessToken());
		assertThat(issuer).isNotNull();
		assertThat(claims.path("iss").asString()).isEqualTo(issuer);
		assertThat(claims.path("sub").asString()).isEqualTo(login.subject());
		assertThat(claims.path("email").asString()).isEqualTo(login.email());
		assertThat(claims.path("name").asString()).isNotEmpty().contains("Alice");
	}

	private JsonNode payloadOf(String jwt) {
		byte[] payload = Base64.getUrlDecoder().decode(jwt.split("\\.")[1]);
		return this.json.readTree(new String(payload, StandardCharsets.UTF_8));
	}

}
