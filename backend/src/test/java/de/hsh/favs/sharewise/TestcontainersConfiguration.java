package de.hsh.favs.sharewise;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final String REALM = "sharewise";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

	/**
	 * One Keycloak for the whole test run, the same image and the same realm file as
	 * docker-compose (D7). No KC_HOSTNAME: the issuer follows the dynamic port of the
	 * request. Readiness is the realm's discovery document, so the import has finished.
	 */
	@Bean
	GenericContainer<?> keycloakContainer() {
		return new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.8.1"))
			.withCommand("start-dev", "--import-realm")
			.withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", KeycloakUsers.ADMIN_USER)
			.withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", KeycloakUsers.ADMIN_PASSWORD)
			.withCopyFileToContainer(MountableFile.forHostPath("../docker/keycloak/sharewise-realm.json"),
					"/opt/keycloak/data/import/sharewise-realm.json")
			.withExposedPorts(8080)
			.waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
				.forPort(8080)
				.forStatusCode(200)
				.withStartupTimeout(Duration.ofMinutes(3)));
	}

	@Bean
	DynamicPropertyRegistrar keycloakProperties(@Qualifier("keycloakContainer") GenericContainer<?> keycloak) {
		return registry -> {
			String baseUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
			KeycloakUsers.configure(baseUrl);
			registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> baseUrl + "/realms/" + REALM);
		};
	}

}
