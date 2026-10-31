package de.hsh.favs.sharewise.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.account.api.AccountRegistered;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.domain.Account;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.account.persistence.AccountRepository;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

	static final Instant NOW = Instant.parse("2027-06-01T10:00:00Z");

	@Mock
	AccountRepository accounts;

	@Mock
	ApplicationEventPublisher events;

	AccountService service;

	AccountFixture alice = AccountFixture.alice();

	@BeforeEach
	void setUp() {
		service = new AccountService(accounts, events, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void unknownSubjectCreatesTheAccountAndPublishesAccountRegistered() {
		// given
		when(accounts.findById(alice.id())).thenReturn(Optional.empty());
		when(accounts.insertIfAbsent(any(Account.class))).thenReturn(true);

		// when
		UserProfile profile = service
			.resolveOrCreate(new IdentityClaims(alice.idString(), alice.email(), "Alice", "alice"));

		// then
		ArgumentCaptor<Account> inserted = ArgumentCaptor.forClass(Account.class);
		verify(accounts).insertIfAbsent(inserted.capture());
		assertThat(inserted.getValue().userId()).isEqualTo(alice.userId());
		verify(events).publishEvent(new AccountRegistered(alice.userId(), new Email(alice.email()), NOW));
		assertThat(profile).isEqualTo(new UserProfile(new UserId(alice.id()), "Alice", true));
	}

	@Test
	void knownSubjectLoadsTheAccountAndPublishesNothing() {
		// given
		when(accounts.findById(alice.id())).thenReturn(Optional.of(alice.build()));

		// when
		UserProfile profile = service
			.resolveOrCreate(new IdentityClaims(alice.idString(), alice.email(), "Another Name", "alice"));

		// then
		assertThat(profile).isEqualTo(alice.profile());
		verify(accounts, never()).insertIfAbsent(any(Account.class));
		verifyNoInteractions(events);
	}

}
