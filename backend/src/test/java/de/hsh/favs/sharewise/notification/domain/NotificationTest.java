package de.hsh.favs.sharewise.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

	static final Instant NOW = MutableClock.START;

	Notification entry = Notification.notify(AccountFixture.alice().userId(), NotificationType.MEMBER_JOINED,
			new GroupId(UUID.randomUUID()), AccountFixture.bob().userId(), UUID.randomUUID(), NOW);

	@Test
	void anEntryIsUnreadUntilItIsMarkedRead() {
		// given
		assertThat(entry.isRead()).isFalse();
		assertThat(entry.readAt()).isNull();

		// when
		entry.markRead(NOW.plusSeconds(10));

		// then
		assertThat(entry.isRead()).isTrue();
		assertThat(entry.readAt()).isEqualTo(NOW.plusSeconds(10));
	}

	@Test
	void markingReadTwiceKeepsTheFirstTime() {
		// given
		entry.markRead(NOW.plusSeconds(10));

		// when
		entry.markRead(NOW.plusSeconds(99));

		// then
		assertThat(entry.readAt()).isEqualTo(NOW.plusSeconds(10));
	}

	@Test
	void theFactoryKeepsWhatHappenedAndToWhom() {
		assertThat(entry.recipient()).isEqualTo(AccountFixture.alice().userId());
		assertThat(entry.actor()).isEqualTo(AccountFixture.bob().userId());
		assertThat(entry.type()).isEqualTo(NotificationType.MEMBER_JOINED);
		assertThat(entry.occurredAt()).isEqualTo(NOW);
	}

}
