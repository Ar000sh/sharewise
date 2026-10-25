package de.hsh.favs.sharewise.account;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class AccountModuleTest {

	static final String ALICE = "aaaaaaaa-0000-0000-0000-000000000001";

	@Autowired
	MockMvc mvc;

	@Test
	void meReturnsCurrentUserForKnownHeader() throws Exception {
		mvc.perform(get("/me").header("X-User-Id", ALICE)).andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(ALICE)).andExpect(jsonPath("$.displayName").value("Alice"));
	}

	@Test
	void missingHeaderIs401Unauthenticated() throws Exception {
		mvc.perform(get("/me")).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void unknownUserIdIs401() throws Exception {
		mvc.perform(get("/me").header("X-User-Id", "aaaaaaaa-0000-0000-0000-0000000000ff"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void malformedUserIdIs401() throws Exception {
		mvc.perform(get("/me").header("X-User-Id", "not-a-uuid")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void usersListsFourSeededUsersWithoutHeader() throws Exception {
		mvc.perform(get("/users")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].userId").value(ALICE)).andExpect(jsonPath("$[0].displayName").value("Alice"))
				.andExpect(jsonPath("$[3].displayName").value("David"));
	}

}
