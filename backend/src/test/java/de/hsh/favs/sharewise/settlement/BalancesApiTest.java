package de.hsh.favs.sharewise.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.hsh.favs.sharewise.TestcontainersConfiguration;
import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, SettlementFakes.class })
class BalancesApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	FakeGroupMembership groups;

	@Autowired
	FakeUserDirectory users;

	@Autowired
	FakeCurrentUser currentUser;

	@Autowired
	BalanceStore store;

	@Autowired
	Balances balances;

	private GroupId group;
	private UserId alice;
	private UserId bob;
	private UserId carol;

	@BeforeEach
	void setUp() {
		group = new GroupId(UUID.randomUUID());
		alice = new UserId(UUID.randomUUID());
		bob = new UserId(UUID.randomUUID());
		carol = new UserId(UUID.randomUUID());
		users.add(alice, "Alice");
		users.add(bob, "Bob");
		users.add(carol, "Carol");
		currentUser.actAs(new UserInfo(alice, "Alice"));
	}

	private MemberId member(UserId user) {
		return new MemberId(user.value());
	}

	@Test
	void listsEveryMemberWithZeroWhenNoExpenses() throws Exception {
		groups.define(group, member(carol), member(alice), member(bob));

		mvc.perform(get("/groups/{id}/balances", group.value().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].displayName").value("Alice"))
				.andExpect(jsonPath("$[1].displayName").value("Bob"))
				.andExpect(jsonPath("$[2].displayName").value("Carol"))
				.andExpect(jsonPath("$[0].balanceCents").value(0))
				.andExpect(jsonPath("$[1].balanceCents").value(0))
				.andExpect(jsonPath("$[2].balanceCents").value(0));
	}

	@Test
	void mergesStoredBalancesWithMemberNames() throws Exception {
		groups.define(group, member(alice), member(bob), member(carol));
		store.add(group, member(alice), 9000);
		store.add(group, member(bob), -9000);

		mvc.perform(get("/groups/{id}/balances", group.value().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].memberId").value(alice.value().toString()))
				.andExpect(jsonPath("$[0].displayName").value("Alice"))
				.andExpect(jsonPath("$[0].balanceCents").value(9000))
				.andExpect(jsonPath("$[1].displayName").value("Bob"))
				.andExpect(jsonPath("$[1].balanceCents").value(-9000))
				.andExpect(jsonPath("$[2].displayName").value("Carol"))
				.andExpect(jsonPath("$[2].balanceCents").value(0));
	}

	@Test
	void sumOfReturnedBalancesIsZero() throws Exception {
		groups.define(group, member(alice), member(bob), member(carol));
		store.add(group, member(alice), 9000);
		store.add(group, member(bob), -4500);
		store.add(group, member(carol), -4500);

		var body = mvc.perform(get("/groups/{id}/balances", group.value().toString()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		List<Number> cents = JsonPath.read(body, "$[*].balanceCents");
		assertThat(cents.stream().mapToLong(Number::longValue).sum()).isZero();
		assertThat(balances.of(group)).hasSize(3);
	}

	@Test
	void nonMemberIs403() throws Exception {
		groups.define(group, member(bob), member(carol));

		mvc.perform(get("/groups/{id}/balances", group.value().toString()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void unknownGroupIs404() throws Exception {
		mvc.perform(get("/groups/{id}/balances", UUID.randomUUID().toString()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

}
