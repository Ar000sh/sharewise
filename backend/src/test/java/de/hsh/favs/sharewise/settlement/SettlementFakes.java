package de.hsh.favs.sharewise.settlement;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Mutable fakes for tests that set up groups, names and the caller themselves. Imported explicitly. */
@TestConfiguration(proxyBeanMethods = false)
class SettlementFakes {

	@Bean
	@Primary
	FakeGroupMembership fakeGroupMembership() {
		return new FakeGroupMembership();
	}

	@Bean
	@Primary
	FakeUserDirectory fakeUserDirectory() {
		return new FakeUserDirectory();
	}

	@Bean
	@Primary
	FakeCurrentUser fakeCurrentUser() {
		return new FakeCurrentUser();
	}

}
