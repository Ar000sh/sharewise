package de.hsh.favs.sharewise.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.account.api.UserDirectory;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class DevIdentityFilterTest {

	@Mock
	UserDirectory users;

	@InjectMocks
	DevIdentityFilter filter;

	MockHttpServletResponse response = new MockHttpServletResponse();

	MockFilterChain chain = new MockFilterChain();

	@AfterEach
	void clearRequest() {
		RequestContextHolder.resetRequestAttributes();
	}

	MockHttpServletRequest request(String path, String userHeader) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setRequestURI(path);
		if (userHeader != null) {
			request.addHeader(DevIdentityFilter.HEADER, userHeader);
		}
		return request;
	}

	@Test
	void knownUserPassesAndBecomesTheCurrentUser() throws Exception {
		// given
		UserFixture bob = UserFixture.bob();
		when(users.find(bob.userId())).thenReturn(Optional.of(bob.info()));
		MockHttpServletRequest request = request("/me", bob.idString());

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(chain.getRequest()).isNotNull();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		assertThat(new CurrentUserImpl().get()).isEqualTo(bob.info());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", "not-a-uuid", "aaaaaaaa-0000-0000-0000-000000000255" })
	void missingBlankMalformedOrUnknownIdIsRejectedWith401(String header) throws Exception {
		// given: the directory does not know the id (a mock returns an empty Optional)
		MockHttpServletRequest request = request("/me", header);

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/problem+json");
		assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
		assertThat(chain.getRequest()).as("the request must not reach the controller").isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator/health", "/users" })
	void actuatorAndUsersAreNotFiltered(String path) {
		assertThat(filter.shouldNotFilter(request(path, null))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/me", "/groups", "/invitations/abc/accept" })
	void everythingElseIsFiltered(String path) {
		assertThat(filter.shouldNotFilter(request(path, null))).isFalse();
	}

}
