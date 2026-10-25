package de.hsh.favs.sharewise.expense;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stand-ins for the Account and Group beans, so the expense module and the full context run without them.
 * They back off as soon as a real implementation exists.
 */
@Configuration(proxyBeanMethods = false)
class ExpenseTestBeans {

	@Bean
	@ConditionalOnMissingBean(GroupMembership.class)
	StubGroupMembership fallbackGroupMembership() {
		return new StubGroupMembership();
	}

	@Bean
	@ConditionalOnMissingBean(CurrentUser.class)
	StubCurrentUser fallbackCurrentUser() {
		return new StubCurrentUser();
	}

}
