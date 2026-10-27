package de.hsh.favs.sharewise.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GroupNameTest {

	@Test
	void aNameIsTrimmedAndMayHaveUpToHundredCharacters() {
		assertThat(new GroupName("  Mallorca 2027 ").value()).isEqualTo("Mallorca 2027");
		assertThat(new GroupName("x".repeat(100)).value()).hasSize(100);
	}

	@ParameterizedTest
	@MethodSource("namesThatAreBlankOrTooLong")
	void aBlankOrTooLongNameIsRejected(String text) {
		assertThatThrownBy(() -> new GroupName(text)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST));
	}

	static Stream<String> namesThatAreBlankOrTooLong() {
		return Stream.of("", "   ", "x".repeat(101));
	}

}
