package de.hsh.favs.sharewise.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import de.hsh.favs.sharewise.KeycloakUsers;
import de.hsh.favs.sharewise.ModuleIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The account is created by the first request of a new Keycloak user (D4). The Next.js
 * callback and the first page load can arrive at the same moment (F1).
 */
@ModuleIntegrationTest
class AccountFirstLoginTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void twoFirstRequestsAtOnceCreateOneAccount() throws Exception {
		// given a Keycloak user that has never called ShareWise, and two requests held at
		// the same start line
		KeycloakUsers.Login login = KeycloakUsers.create("Fiona");
		UUID id = UUID.fromString(login.subject());
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> firstRequest = () -> {
			ready.countDown();
			start.await();
			return mvc.perform(get("/me").header("Authorization", "Bearer " + login.accessToken()))
				.andReturn()
				.getResponse()
				.getStatus();
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);

		// when
		List<Integer> statuses;
		try {
			Future<Integer> first = pool.submit(firstRequest);
			Future<Integer> second = pool.submit(firstRequest);
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			statuses = List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
		}
		finally {
			pool.shutdownNow();
		}

		// then both are answered and there is one account. The race only exercises the
		// guard when both requests are in flight together; this end state holds either
		// way.
		assertThat(statuses).containsExactly(200, 200);
		assertThat(jdbc.queryForObject("select count(*) from accounts where id = ?", Integer.class, id)).isEqualTo(1);
	}

}
