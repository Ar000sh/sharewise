package de.hsh.favs.sharewise.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

	@Test
	void twoSpellingsOfOneAddressAreEqual() {
		assertThat(new Email("  Alice@Example.COM ")).isEqualTo(new Email("alice@example.com"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "alice", "alice@", "@example.com", "alice@example", "a b@example.com" })
	void somethingThatIsNotAnAddressIsRejected(String text) {
		assertThatThrownBy(() -> new Email(text)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST));
	}

	@Test
	void anAddressLongerThan254CharactersIsRejected() {
		String tooLong = "a".repeat(250) + "@example.com";

		assertThatThrownBy(() -> new Email(tooLong)).isInstanceOf(DomainException.class);
	}

}
