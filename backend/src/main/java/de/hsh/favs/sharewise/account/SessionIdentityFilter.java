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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code Authorization: Bearer <token>} into the caller. A bad token is refused here. A request without an
 * Authorization header passes with no caller bound, so {@code CurrentUser.get()} answers 401 on every protected endpoint.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SessionIdentityFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final AuthService auth;

	SessionIdentityFilter(AuthService auth) {
		this.auth = auth;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/actuator") || path.startsWith("/auth/");
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
