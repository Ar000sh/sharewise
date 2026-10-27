package de.hsh.favs.sharewise.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** The port other modules call, with real accounts and called outside any transaction. */
@ModuleIntegrationTest
class UserProfilesImplTest {

	@Autowired
	UserProfiles profiles;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void findsTheProfilesOfExistingAccountsAndSkipsUnknownIds() throws Exception {
		// given
		AccountFixture dora = AccountFixture.signUp(mvc, json, "Dora");
		AccountFixture emil = AccountFixture.signUp(mvc, json, "Emil");
		UserId unknown = new UserId(AccountFixture.unknownId());

		// when
		var found = profiles.findProfiles(Set.of(dora.userId(), emil.userId(), unknown));

		// then
		assertThat(found).containsExactlyInAnyOrder(new UserProfile(dora.userId(), "Dora", true),
				new UserProfile(emil.userId(), "Emil", true));
		assertThat(profiles.find(dora.userId())).contains(new UserProfile(dora.userId(), "Dora", true));
		assertThat(profiles.find(unknown)).isEmpty();
	}

}
