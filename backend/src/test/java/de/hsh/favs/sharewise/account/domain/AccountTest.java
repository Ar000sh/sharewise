package de.hsh.favs.sharewise.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.account.api.AccountRegistered;
import de.hsh.favs.sharewise.account.api.LoginFailed;
import de.hsh.favs.sharewise.account.api.LoginSucceeded;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AccountTest {

	static final Instant NOW = Instant.parse("2027-06-01T10:00:00Z");

	Hasher hasher = new StubHasher();

	AccountFixture alice = AccountFixture.alice();

	@Test
	void registeringCreatesAnActiveAccountWithAHashedPasswordAndAnEvent() {
		// when
		Account.Registration registration = Account.register(new Email("Alice@Example.com"), new DisplayName(" Alice "),
				new RawPassword("secret-pass"), hasher, NOW);

		// then
		Account account = registration.account();
		assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.email()).isEqualTo(new Email("alice@example.com"));
		assertThat(account.displayName()).isEqualTo(new DisplayName("Alice"));
		assertThat(ReflectionTestUtils.getField(account, "passwordHash")).isEqualTo("hash:secret-pass");
		assertThat(registration.event()).isEqualTo(new AccountRegistered(account.userId(), account.email(), NOW));
	}

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

	@Test
	void theRightPasswordSucceedsResetsTheFailureCounterAndRemembersTheLogin() {
		// given an account with one failed attempt
		Account account = alice.build();
		account.login(new RawPassword("wrong-pass"), hasher, NOW);

		// when
		var outcome = account.login(new RawPassword(AccountFixture.PASSWORD), hasher, NOW.plusSeconds(60));

		// then
		assertThat(outcome).isEqualTo(new LoginSucceeded(account.userId(), NOW.plusSeconds(60)));
		assertThat(account.failedLoginAttempts()).isZero();
		assertThat(account.lastLoginAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void aWrongPasswordFailsAndIsCounted() {
		// given
		Account account = alice.build();

		// when
		var first = account.login(new RawPassword("wrong-pass"), hasher, NOW);
		account.login(new RawPassword("wrong-pass"), hasher, NOW);

		// then
		assertThat(first).isEqualTo(new LoginFailed(account.userId(), NOW));
		assertThat(account.failedLoginAttempts()).isEqualTo(2);
		assertThat(account.lastLoginAt()).isNull();
	}

	@Test
	void anAccountThatIsNotActiveCannotSignInEvenWithTheRightPassword() {
		// given
		Account account = alice.build();
		ReflectionTestUtils.setField(account, "status", AccountStatus.LOCKED);

		// when
		var outcome = account.login(new RawPassword(AccountFixture.PASSWORD), hasher, NOW);

		// then
		assertThat(outcome).isInstanceOf(LoginFailed.class);
		assertThat(account.lastLoginAt()).isNull();
	}

}
