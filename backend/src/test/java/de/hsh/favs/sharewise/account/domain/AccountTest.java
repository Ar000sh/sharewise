package de.hsh.favs.sharewise.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.account.api.AccountRegistered;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {

	static final Instant NOW = Instant.parse("2027-06-01T10:00:00Z");

	@Test
	void registerFromIdentityProviderKeepsTheGivenIdAndStartsActive() {
		// given
		UserId subject = new UserId(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"));

		// when
		Account account = Account
			.registerFromIdentityProvider(subject, new Email("alice@example.com"), new DisplayName("Alice"), NOW)
			.account();

		// then
		assertThat(account.userId()).isEqualTo(subject);
		assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.email()).isEqualTo(new Email("alice@example.com"));
		assertThat(account.displayName()).isEqualTo(new DisplayName("Alice"));
		assertThat(account.registeredAt()).isEqualTo(NOW);
	}

	@Test
	void registerFromIdentityProviderAnnouncesAccountRegistered() {
		// given
		UserId subject = new UserId(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"));

		// when
		Account.Registration registration = Account.registerFromIdentityProvider(subject,
				new Email("alice@example.com"), new DisplayName("Alice"), NOW);

		// then
		assertThat(registration.event()).isEqualTo(new AccountRegistered(subject, new Email("alice@example.com"), NOW));
	}

}
