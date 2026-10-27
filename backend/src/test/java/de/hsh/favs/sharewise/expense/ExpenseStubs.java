package de.hsh.favs.sharewise.expense;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The expense integration tests drive group membership and the caller themselves, so
 * these beans win over the real Account and Group implementations once those are merged.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ExpenseStubs {

	@Bean
	@Primary
	StubGroupMembership primaryGroupMembership() {
		return new StubGroupMembership();
	}

	@Bean
	@Primary
	StubCurrentUser primaryCurrentUser() {
		return new StubCurrentUser();
	}

}
