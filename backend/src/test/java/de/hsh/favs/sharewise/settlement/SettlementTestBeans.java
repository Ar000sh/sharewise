package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserDirectory;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stand-ins so the full application context loads on this branch, before the real account and group beans are
 * merged. They back off as soon as a real bean exists.
 */
@Configuration(proxyBeanMethods = false)
class SettlementTestBeans {

	@Bean
	@ConditionalOnMissingBean(GroupMembership.class)
	GroupMembership settlementFallbackGroupMembership() {
		return new FakeGroupMembership();
	}

	@Bean
	@ConditionalOnMissingBean(UserDirectory.class)
	UserDirectory settlementFallbackUserDirectory() {
		return new FakeUserDirectory();
	}

	@Bean
	@ConditionalOnMissingBean(CurrentUser.class)
	CurrentUser settlementFallbackCurrentUser() {
		return new FakeCurrentUser();
	}

}
