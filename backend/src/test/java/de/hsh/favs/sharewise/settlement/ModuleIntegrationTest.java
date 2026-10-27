package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.TestcontainersConfiguration;
import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Full application on a real Postgres (Testcontainers). The beans of the other modules
 * are mocks, because this track cannot use their code. All integration tests of the
 * module share this one context.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@MockitoBean(types = { GroupMembership.class, UserProfiles.class, CurrentUser.class })
public @interface ModuleIntegrationTest {

}
