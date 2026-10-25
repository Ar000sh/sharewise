package de.hsh.favs.sharewise.settlement;

import static org.mockito.Mockito.mock;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserDirectory;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lets {@code SharewiseApplicationTests.contextLoads} start before the real account and group beans are
 * merged. Mocks that back off as soon as a real bean exists.
 */
@Configuration(proxyBeanMethods = false)
class SettlementTestBeans {

	@Bean
	@ConditionalOnMissingBean(GroupMembership.class)
	GroupMembership groupMembershipStandIn() {
		return mock(GroupMembership.class);
	}

	@Bean
	@ConditionalOnMissingBean(UserDirectory.class)
	UserDirectory userDirectoryStandIn() {
		return mock(UserDirectory.class);
	}

	@Bean
	@ConditionalOnMissingBean(CurrentUser.class)
	CurrentUser currentUserStandIn() {
		return mock(CurrentUser.class);
	}

}
