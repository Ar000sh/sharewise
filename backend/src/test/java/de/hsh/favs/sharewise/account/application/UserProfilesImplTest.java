package de.hsh.favs.sharewise.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.KeycloakUsers;
import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.account.domain.AccountStatus;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void findsTheProfilesOfExistingAccountsAndSkipsUnknownIds() throws Exception {
		// given
		AccountFixture dora = AccountFixture.signUp(mvc, json, "Dora");
		AccountFixture emil = AccountFixture.signUp(mvc, json, "Emil");
		UserId unknown = new UserId(AccountFixture.unknownId());

		// when
		var found = profiles.findProfiles(Set.of(dora.userId(), emil.userId(), unknown));

		// then
		assertThat(found).containsExactlyInAnyOrder(dora.profile(), emil.profile());
		assertThat(profiles.find(dora.userId())).contains(dora.profile());
		assertThat(profiles.find(unknown)).isEmpty();
	}

	@Test
	void findsByUsernameOrByEmailIgnoringCaseAndSpaces() throws Exception {
		// given
		AccountFixture gina = AccountFixture.signUp(mvc, json, "Gina");

		// when / then
		assertThat(profiles.findByIdentifier(" " + gina.username().toUpperCase() + " ")).contains(gina.profile());
		assertThat(profiles.findByIdentifier(gina.email().toUpperCase())).contains(gina.profile());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "%", "_", "gin%", "nobody-here-at-all", "@" })
	void unknownBlankAndWildcardIdentifiersFindNobody(String identifier) throws Exception {
		// given an account whose name a wildcard would match
		AccountFixture.signUp(mvc, json, "Gina");

		// when / then
		assertThat(profiles.findByIdentifier(identifier)).isEmpty();
	}

	@Test
	void anInactiveAccountIsNotFound() throws Exception {
		// given
		AccountFixture hugo = AccountFixture.signUp(mvc, json, "Hugo");
		jdbc.update("update accounts set status = ? where id = ?", AccountStatus.DEACTIVATED.name(), hugo.id());

		// when / then
		assertThat(profiles.findByIdentifier(hugo.username())).isEmpty();
		assertThat(profiles.findByIdentifier(hugo.email())).isEmpty();
	}

	@Test
	void aKeycloakUserWhoNeverCalledMeIsNotFoundUntilTheFirstRequest() throws Exception {
		// given a Keycloak user that never opened ShareWise
		KeycloakUsers.Login login = KeycloakUsers.create("Ida");

		// when / then not found yet
		assertThat(profiles.findByIdentifier(login.username())).isEmpty();
		mvc.perform(get("/me").header("Authorization", "Bearer " + login.accessToken())).andExpect(status().isOk());
		assertThat(profiles.findByIdentifier(login.username())).isPresent();
	}

}
