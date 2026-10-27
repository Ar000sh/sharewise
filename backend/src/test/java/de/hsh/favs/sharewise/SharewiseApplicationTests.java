package de.hsh.favs.sharewise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SharewiseApplicationTests {

	@Autowired
	Environment environment;

	@Test
	void contextLoads() {
	}

	@Test
	void eventsThatWereNeverFinishedAreDeliveredAgainWhenTheApplicationStarts() {
		// Without this a failed balance update would be lost for good (found by the
		// independent review).
		assertThat(environment.getProperty("spring.modulith.events.republish-outstanding-events-on-restart",
				Boolean.class))
			.isTrue();
	}

}
