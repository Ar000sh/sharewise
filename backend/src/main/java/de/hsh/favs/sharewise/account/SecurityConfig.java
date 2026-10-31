package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.shared.ErrorCode;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The backend is an OAuth2 resource server (D3): every request needs a valid Keycloak
 * access token except {@code /actuator/**}. Stateless and without CSRF protection,
 * because the token comes in the {@code Authorization} header and this API sets no
 * cookies (the cookie lives in the Next.js server, D2). A missing or refused token is
 * answered with the contract's 401 problem, code UNAUTHENTICATED (F8).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	private static final String UNAUTHENTICATED_BODY = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\","
			+ "\"status\":401,\"code\":\"UNAUTHENTICATED\",\"detail\":\"Missing or invalid token\"}";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, Converter<Jwt, AbstractAuthenticationToken> caller)
			throws Exception {
		AuthenticationEntryPoint unauthenticated = (request, response, refused) -> writeUnauthenticated(response);
		return http.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				// the error dispatch keeps the status Spring already chose (404, 405,
				// ...)
				// instead of turning it into a 401
				.dispatcherTypeMatchers(DispatcherType.ERROR)
				.permitAll()
				.requestMatchers("/actuator/**")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(server -> server.authenticationEntryPoint(unauthenticated)
				.jwt(jwt -> jwt.jwtAuthenticationConverter(caller)))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthenticated))
			.build();
	}

	private static void writeUnauthenticated(HttpServletResponse response) throws IOException {
		response.setStatus(ErrorCode.UNAUTHENTICATED.status());
		response.setHeader("WWW-Authenticate", "Bearer");
		response.setContentType("application/problem+json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(UNAUTHENTICATED_BODY);
	}

}
