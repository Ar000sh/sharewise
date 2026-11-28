package de.hsh.favs.sharewise.group;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled jobs for the group module (e.g. persisting expired invitations, R9).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sharewise.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class GroupConfig {

}
