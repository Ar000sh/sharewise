package de.hsh.favs.sharewise.expense.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.persistence.MembershipCopyRepository;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** The group context's real events reach the expense context's copy. */
@ModuleIntegrationTest
class MembershipCopyListenerTest {

	@Autowired
	MembershipCopyRepository copy;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void aNewGroupWithItsCreatorAndAJoinedInviteeAppearsInTheCopy() throws Exception {
		// given / when a group is created and bob joins through an invitation
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		GroupId group = new GroupId(new GroupRequests(mvc, json).groupWith(alice, "Trip", bob));

		// then
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			var view = copy.find(group).orElseThrow();
			assertThat(view.isOpen()).isTrue();
			assertThat(view.activeMembers()).containsExactlyInAnyOrder(alice.userId(), bob.userId());
			assertThat(view.admins()).containsExactly(alice.userId());
		});
	}

}
