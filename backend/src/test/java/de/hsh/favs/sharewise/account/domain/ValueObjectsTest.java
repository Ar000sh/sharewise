package de.hsh.favs.sharewise.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The value objects of the account context: what they accept and what they hide. */
class ValueObjectsTest {

	@ParameterizedTest
	@MethodSource("passwordsOfTheWrongLength")
	void aPasswordOutsideEightToSeventyTwoCharactersIsRejected(String text) {
		assertThatThrownBy(() -> new RawPassword(text)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST));
	}

	@Test
	void aPasswordNeverShowsItsTextWhenPrinted() {
		assertThat(new RawPassword("secret-pass").toString()).doesNotContain("secret");
		assertThat(new PasswordHash("$2a$10$abc").toString()).doesNotContain("abc");
	}

	@Test
	void aDisplayNameIsTrimmed() {
		assertThat(new DisplayName("  Alice  ").value()).isEqualTo("Alice");
	}

	@ParameterizedTest
	@MethodSource("displayNamesThatAreBlankOrTooLong")
	void aBlankOrTooLongDisplayNameIsRejected(String text) {
		assertThatThrownBy(() -> new DisplayName(text)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST));
	}

	static Stream<String> passwordsOfTheWrongLength() {
		return Stream.of("short", "x".repeat(73));
	}

	static Stream<String> displayNamesThatAreBlankOrTooLong() {
		return Stream.of("", "   ", "x".repeat(101));
	}

}
