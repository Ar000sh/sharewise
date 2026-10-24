package de.hsh.favs.sharewise;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void verifiesModuleStructure() {
		ApplicationModules.of(SharewiseApplication.class).verify();
	}

}
