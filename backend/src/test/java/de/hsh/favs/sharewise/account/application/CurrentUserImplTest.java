package de.hsh.favs.sharewise.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class CurrentUserImplTest {

	CurrentUserImpl currentUser = new CurrentUserImpl();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void returnsTheCallerOfTheValidatedToken() {
		// given
		SecurityContextHolder.getContext()
			.setAuthentication(
					new PreAuthenticatedAuthenticationToken(AccountFixture.alice().profile(), "jwt", List.of()));

		// when / then
		assertThat(currentUser.get()).isEqualTo(AccountFixture.alice().profile());
	}

	@Test
	void isUnauthenticatedForAnAnonymousCaller() {
		// given
		SecurityContextHolder.getContext()
			.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
					List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		// when / then
		assertThatThrownBy(currentUser::get).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
	}

	@Test
	void isUnauthenticatedOutsideOfARequest() {
		// e.g. when called from an event listener thread: the security context is empty
		assertThatThrownBy(currentUser::get).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
	}

}
