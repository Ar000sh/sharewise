package de.hsh.favs.sharewise.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import de.hsh.favs.sharewise.KeycloakUsers;
import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.api.AccountRegistered;
import de.hsh.favs.sharewise.shared.UserId;
import java.lang.reflect.Method;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.ApplicationEventsHolder;
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

	@Autowired
	ApplicationEvents events;

	/**
	 * What one of the concurrent requests saw: its status and the events it published.
	 */
	record FirstRequest(int status, long accountRegistered) {
	}

	@Test
	void twoFirstRequestsAtOnceCreateOneAccountAndOneEvent() throws Exception {
		// given a Keycloak user that has never called ShareWise, and two requests held at
		// the same start line
		KeycloakUsers.Login login = KeycloakUsers.create("Fiona");
		UserId id = new UserId(UUID.fromString(login.subject()));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<FirstRequest> firstRequest = () -> {
			recordEventsOfThisThread(true);
			try {
				ready.countDown();
				start.await();
				int status = mvc.perform(get("/me").header("Authorization", "Bearer " + login.accessToken()))
					.andReturn()
					.getResponse()
					.getStatus();
				long registered = ApplicationEventsHolder.getRequiredApplicationEvents()
					.stream(AccountRegistered.class)
					.filter(event -> event.userId().equals(id))
					.count();
				return new FirstRequest(status, registered);
			}
			finally {
				recordEventsOfThisThread(false);
			}
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);

		// when
		List<FirstRequest> results;
		try {
			Future<FirstRequest> first = pool.submit(firstRequest);
			Future<FirstRequest> second = pool.submit(firstRequest);
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			results = List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
		}
		finally {
			pool.shutdownNow();
		}

		// then
		assertThat(results).extracting(FirstRequest::status).containsExactly(200, 200);
		assertThat(jdbc.queryForObject("select count(*) from accounts where id = ?", Integer.class, id.value()))
			.isEqualTo(1);
		long published = results.stream().mapToLong(FirstRequest::accountRegistered).sum()
				+ events.stream(AccountRegistered.class).filter(event -> event.userId().equals(id)).count();
		assertThat(published).isEqualTo(1);
	}

	// ApplicationEvents records per thread and offers no public way to record in a thread
	// the test starts itself, so each request thread registers its own recorder.
	private static void recordEventsOfThisThread(boolean on) throws Exception {
		Method method = ApplicationEventsHolder.class
			.getDeclaredMethod(on ? "registerApplicationEvents" : "unregisterApplicationEvents");
		method.setAccessible(true);
		method.invoke(null);
	}

}
