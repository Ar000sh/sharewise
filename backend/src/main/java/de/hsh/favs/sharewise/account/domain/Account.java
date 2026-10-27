package de.hsh.favs.sharewise.account.domain;

import de.hsh.favs.sharewise.account.api.AccountEvent;
import de.hsh.favs.sharewise.account.api.AccountRegistered;
import de.hsh.favs.sharewise.account.api.LoginFailed;
import de.hsh.favs.sharewise.account.api.LoginSucceeded;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A person's account: the sign-in name (email), the hashed password and the name others
 * see. Aggregate root of the account context (generic subdomain). Its id is the person's
 * {@link UserId} everywhere else in the system.
 */
@Entity
@Table(name = "accounts")
public class Account {

	/** An account together with the event that announces it. */
	public record Registration(Account account, AccountRegistered event) {

	}

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	protected Account() {
	}

	/**
	 * Creates an ACTIVE account. The password is hashed here and the raw text is not
	 * kept.
	 */
	public static Registration register(Email email, DisplayName displayName, RawPassword password, Hasher hasher,
			Instant now) {
		Account account = new Account();
		account.id = UUID.randomUUID();
		account.email = email.value();
		account.displayName = displayName.value();
		account.passwordHash = hasher.hashPassword(password).value();
		account.status = AccountStatus.ACTIVE;
		account.registeredAt = now;
		return new Registration(account, new AccountRegistered(account.userId(), email, now));
	}

	/**
	 * Checks a sign-in attempt. Only an ACTIVE account with the right password succeeds;
	 * the outcome is returned, not thrown, because a failed attempt must still be saved
	 * (it is counted).
	 * @return {@link LoginSucceeded} (attempt counter reset, last login set) or
	 * {@link LoginFailed}
	 */
	public AccountEvent login(RawPassword password, Hasher hasher, Instant now) {
		boolean correct = status == AccountStatus.ACTIVE
				&& hasher.matchesPassword(password, new PasswordHash(passwordHash));
		if (!correct) {
			failedLoginAttempts++;
			return new LoginFailed(userId(), now);
		}
		failedLoginAttempts = 0;
		lastLoginAt = now;
		return new LoginSucceeded(userId(), now);
	}

	public UserId userId() {
		return new UserId(id);
	}

	public Email email() {
		return new Email(email);
	}

	public DisplayName displayName() {
		return new DisplayName(displayName);
	}

	public AccountStatus status() {
		return status;
	}

	public int failedLoginAttempts() {
		return failedLoginAttempts;
	}

	public Instant registeredAt() {
		return registeredAt;
	}

	public Instant lastLoginAt() {
		return lastLoginAt;
	}

	public boolean isActive() {
		return status == AccountStatus.ACTIVE;
	}

}
