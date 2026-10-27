package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.TestcontainersConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Full application on a real Postgres (Testcontainers) , a {@link MutableClock} and
 * recorded application events. All integration tests share this one context.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import({ TestcontainersConfiguration.class, TestClockConfig.class })
public @interface ModuleIntegrationTest {

}
