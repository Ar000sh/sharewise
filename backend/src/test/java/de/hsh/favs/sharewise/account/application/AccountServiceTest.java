package de.hsh.favs.sharewise.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.account.api.AccountRegistered;
import de.hsh.favs.sharewise.account.api.LoginFailed;
import de.hsh.favs.sharewise.account.api.LoginSucceeded;
import de.hsh.favs.sharewise.account.domain.Account;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.account.domain.AuthSession;
import de.hsh.favs.sharewise.account.domain.DisplayName;
import de.hsh.favs.sharewise.account.domain.RawPassword;
import de.hsh.favs.sharewise.account.domain.StubHasher;
import de.hsh.favs.sharewise.account.persistence.AccountRepository;
import de.hsh.favs.sharewise.account.persistence.AuthSessionRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.ErrorCode;
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
	AuthSessionRepository sessions;

	@Mock
	ApplicationEventPublisher events;

	AccountService service;

	AccountFixture alice = AccountFixture.alice();

	@BeforeEach
	void setUp() {
		service = new AccountService(accounts, sessions, new StubHasher(), events, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void registeringSavesTheAccountPublishesTheEventAndStartsASession() {
		// given
		when(accounts.existsByEmail("alice@example.com")).thenReturn(false);

		// when
		var signedIn = service.register(new RegisterCommand(new Email(alice.email()), new DisplayName("Alice"),
				new RawPassword(AccountFixture.PASSWORD)));

		// then
		ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
		verify(accounts).saveAndFlush(saved.capture());
		verify(events).publishEvent(new AccountRegistered(saved.getValue().userId(), new Email(alice.email()), NOW));
		ArgumentCaptor<AuthSession> session = ArgumentCaptor.forClass(AuthSession.class);
		verify(sessions).save(session.capture());
		assertThat(session.getValue().userId()).isEqualTo(saved.getValue().userId().value());
		assertThat(signedIn.token()).isNotBlank();
		assertThat(signedIn.profile().displayName()).isEqualTo("Alice");
	}

	@Test
	void registeringATakenEmailIsRefusedAndNothingIsSavedOrPublished() {
		// given
		when(accounts.existsByEmail("alice@example.com")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> service.register(new RegisterCommand(new Email(alice.email()),
				new DisplayName("Alice"), new RawPassword(AccountFixture.PASSWORD))))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_TAKEN));
		verify(accounts, never()).saveAndFlush(any());
		verifyNoInteractions(events, sessions);
	}

	@Test
	void signingInWithTheRightPasswordStartsASession() {
		// given
		Account account = alice.build();
		when(accounts.findByEmail("alice@example.com")).thenReturn(Optional.of(account));

		// when
		var signedIn = service.login(new LoginCommand(new Email(alice.email()), AccountFixture.PASSWORD));

		// then
		verify(events).publishEvent(new LoginSucceeded(account.userId(), NOW));
		verify(sessions).save(any(AuthSession.class));
		assertThat(signedIn.profile().userId()).isEqualTo(account.userId());
	}

	@Test
	void aWrongPasswordIsCountedPublishedAndRefusedWithoutASession() {
		// given
		Account account = alice.build();
		when(accounts.findByEmail("alice@example.com")).thenReturn(Optional.of(account));

		// when / then
		assertThatThrownBy(() -> service.login(new LoginCommand(new Email(alice.email()), "wrong-pass")))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
		assertThat(account.failedLoginAttempts()).isEqualTo(1);
		verify(accounts).save(account);
		verify(events).publishEvent(new LoginFailed(account.userId(), NOW));
		verifyNoInteractions(sessions);
	}

	@Test
	void anUnknownEmailAndATooShortPasswordGetTheSameAnswerAsAWrongPassword() {
		// given
		when(accounts.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
		when(accounts.findByEmail("alice@example.com")).thenReturn(Optional.of(alice.build()));

		// when / then
		assertThatThrownBy(() -> service.login(new LoginCommand(new Email("nobody@example.com"), "whatever1")))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
		assertThatThrownBy(() -> service.login(new LoginCommand(new Email(alice.email()), "x"))).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
		verifyNoInteractions(events, sessions);
	}

	@Test
	void aTokenResolvesToTheProfileOfItsAccountAndAnUnknownTokenToNothing() {
		// given
		Account account = alice.build();
		when(sessions.findById("token-hash:good"))
			.thenReturn(Optional.of(new AuthSession("token-hash:good", account.userId().value(), NOW)));
		when(accounts.findById(account.userId().value())).thenReturn(Optional.of(account));
		when(sessions.findById("token-hash:bad")).thenReturn(Optional.empty());

		// when / then
		assertThat(service.resolveSession("good")).contains(alice.profile());
		assertThat(service.resolveSession("bad")).isEmpty();
	}

}
