package de.hsh.favs.sharewise.group.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupDirectory;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * The port the notification module calls, with real groups and no surrounding
 * transaction.
 */
@ModuleIntegrationTest
class GroupDirectoryImplTest {

	@Autowired
	GroupDirectory directory;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void returnsNamesAndLeavesUnknownIdsOut() throws Exception {
		// given
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		GroupId mallorca = new GroupId(new GroupRequests(mvc, json).createGroup(alice, "Mallorca 2027"));
		GroupId unknown = new GroupId(UUID.randomUUID());

		// when
		var names = directory.findNames(Set.of(mallorca, unknown));

		// then
		assertThat(names).containsOnlyKeys(mallorca).containsEntry(mallorca, "Mallorca 2027");
	}

	@Test
	void anEmptySetFindsNothing() {
		assertThat(directory.findNames(Set.of())).isEmpty();
	}

}
