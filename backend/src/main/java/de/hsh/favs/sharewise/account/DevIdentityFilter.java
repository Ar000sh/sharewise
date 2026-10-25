package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.UserDirectory;
import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stub identity: resolves the X-User-Id header into the caller. Registered only under the dev profile, so the
 * header is never trusted elsewhere. Keycloak later replaces this filter with JWT validation.
 */
@Component
@Profile("dev")
class DevIdentityFilter extends OncePerRequestFilter {

	static final String HEADER = "X-User-Id";

	private final UserDirectory users;

	DevIdentityFilter(UserDirectory users) {
		this.users = users;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/actuator") || path.equals("/users");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<UserInfo> caller = resolve(request.getHeader(HEADER));
		if (caller.isEmpty()) {
			unauthenticated(response);
			return;
		}
		CurrentUserImpl.bind(request, caller.get());
		chain.doFilter(request, response);
	}

	private Optional<UserInfo> resolve(String header) {
		if (header == null || header.isBlank()) {
			return Optional.empty();
		}
		try {
			return users.find(new UserId(UUID.fromString(header.trim())));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static void unauthenticated(HttpServletResponse response) throws IOException {
		response.setStatus(ErrorCode.UNAUTHENTICATED.status());
		response.setContentType("application/problem+json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
				+ "\"code\":\"UNAUTHENTICATED\",\"detail\":\"Missing or unknown X-User-Id\"}");
	}

}
