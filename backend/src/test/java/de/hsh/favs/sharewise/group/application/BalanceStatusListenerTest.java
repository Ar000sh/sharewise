package de.hsh.favs.sharewise.group.application;

import static org.mockito.Mockito.verify;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.events.MemberBalanceStatusChanged;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The listener only hands the ledger's balance status to the group service. */
@ExtendWith(MockitoExtension.class)
class BalanceStatusListenerTest {

	@Mock
	GroupService groups;

	@Test
	void aBalanceStatusChangeIsHandedToTheGroupService() {
		// given
		var event = new MemberBalanceStatusChanged(new GroupId(UUID.randomUUID()), AccountFixture.bob().userId(), false,
				MutableClock.START);

		// when
		new BalanceStatusListener(groups).on(event);

		// then
		verify(groups).updateBalanceStatus(event.groupId(), event.userId(), false, MutableClock.START);
	}

}
