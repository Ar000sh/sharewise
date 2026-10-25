package de.hsh.favs.sharewise.group;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GroupConfig {

	/** Injected wherever time matters so tests can move it (invitation expiry). */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
