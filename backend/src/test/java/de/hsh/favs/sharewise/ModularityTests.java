package de.hsh.favs.sharewise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void detectsTheFourContextsAndSharedTypes() {
		List<String> names = ApplicationModules.of(SharewiseApplication.class).stream()
				.map(m -> m.getIdentifier().toString()).toList();

		assertThat(names).containsExactlyInAnyOrder("account", "group", "expense", "settlement", "shared");
	}

	@Test
	void verifiesModuleStructure() {
		ApplicationModules.of(SharewiseApplication.class).verify();
	}

}
