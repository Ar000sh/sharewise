package de.hsh.favs.sharewise.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UsernameTest {

	@Test
	void lowerCasesAndTrims() {
		// when
		Username username = new Username("  Alice.B-1 ");

		// then
		assertThat(username.value()).isEqualTo("alice.b-1");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "ab", "bob@example.com", "a b", "ali/ce", "ünder" })
	void refusesWhatIsNotAUsername(String text) {
		assertThatThrownBy(() -> new Username(text)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST));
	}

	@Test
	void refusesMoreThanThirtyCharacters() {
		assertThat(new Username("a".repeat(30)).value()).hasSize(30);
		assertThatThrownBy(() -> new Username("a".repeat(31))).isInstanceOf(DomainException.class);
	}

	@Test
	void refusesNull() {
		assertThatThrownBy(() -> new Username(null)).isInstanceOf(DomainException.class);
	}

}
