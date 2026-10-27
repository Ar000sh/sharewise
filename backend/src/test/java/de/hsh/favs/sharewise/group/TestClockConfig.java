package de.hsh.favs.sharewise.group;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the system clock with a {@link MutableClock} that wins over
 * {@code GroupConfig.clock()}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

}
