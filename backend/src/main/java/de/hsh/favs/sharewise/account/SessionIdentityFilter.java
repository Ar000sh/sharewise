package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code Authorization: Bearer <token>} into the caller, in every profile. A bad token is refused here. A
 * request without an Authorization header passes with no caller bound: under the dev profile
 * {@link DevIdentityFilter} may still bind one from X-User-Id, otherwise {@code CurrentUser.get()} answers 401.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SessionIdentityFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final AuthService auth;

	private final boolean devProfile;

	SessionIdentityFilter(AuthService auth, Environment environment) {
		this.auth = auth;
		this.devProfile = environment.acceptsProfiles(Profiles.of("dev"));
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/actuator") || path.startsWith("/auth/") || (devProfile && path.equals("/users"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header == null) {
			// No credentials: nothing is bound, so CurrentUser.get() answers 401 on every protected endpoint.
			chain.doFilter(request, response);
			return;
		}
		Optional<UserInfo> caller = header.startsWith(BEARER) ? auth.resolve(header.substring(BEARER.length()).trim())
				: Optional.empty();
		if (caller.isEmpty()) {
			unauthenticated(response);
			return;
		}
		CurrentUserImpl.bind(request, caller.get());
		chain.doFilter(request, response);
	}

	private static void unauthenticated(HttpServletResponse response) throws IOException {
		response.setStatus(ErrorCode.UNAUTHENTICATED.status());
		response.setContentType("application/problem+json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
				+ "\"code\":\"UNAUTHENTICATED\",\"detail\":\"Missing or invalid session\"}");
	}

}
