package de.hsh.favs.sharewise.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CurrentUserImplTest {

	CurrentUserImpl currentUser = new CurrentUserImpl();

	MockHttpServletRequest request = new MockHttpServletRequest();

	@AfterEach
	void clearRequest() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void returnsTheCallerBoundToTheCurrentRequest() {
		// given
		CurrentUserImpl.bind(request, UserFixture.alice().info());
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		// when / then
		assertThat(currentUser.get()).isEqualTo(UserFixture.alice().info());
	}

	@Test
	void isUnauthenticatedWhenNoCallerWasBound() {
		// given
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		// when / then
		assertThatThrownBy(currentUser::get).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
	}

	@Test
	void isUnauthenticatedOutsideOfARequest() {
		// e.g. when called from an event listener thread
		assertThatThrownBy(currentUser::get).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
	}

}
