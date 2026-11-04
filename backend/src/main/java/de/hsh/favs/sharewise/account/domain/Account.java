package de.hsh.favs.sharewise.account.domain;

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
 * A person's account: the email and the name others see, under the id the identity
 * provider (Keycloak) gave the person. Aggregate root of the account context (generic
 * subdomain); passwords and sign-in belong to the identity provider, never to ShareWise.
 * Its id is the person's {@link UserId} everywhere else in the system.
 */
@Entity
@Table(name = "accounts")
public class Account {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	protected Account() {
	}

	/**
	 * Creates the ACTIVE account of a person who signed in at the identity provider for
	 * the first time. The id is the provider's subject, so the same person always maps to
	 * the same {@link UserId} (D4); email, username and display name are taken once and
	 * owned by ShareWise afterwards (D10, C2).
	 * @return the new account; the Account context draws no events in the diagram
	 */
	public static Account registerFromIdentityProvider(UserId id, Email email, Username username,
			DisplayName displayName, Instant now) {
		Account account = new Account();
		account.id = id.value();
		account.email = email.value();
		account.username = username.value();
		account.displayName = displayName.value();
		account.status = AccountStatus.ACTIVE;
		account.registeredAt = now;
		return account;
	}

	public UserId userId() {
		return new UserId(id);
	}

	public Email email() {
		return new Email(email);
	}

	public Username username() {
		return new Username(username);
	}

	public DisplayName displayName() {
		return new DisplayName(displayName);
	}

	public AccountStatus status() {
		return status;
	}

	public Instant registeredAt() {
		return registeredAt;
	}

	public boolean isActive() {
		return status == AccountStatus.ACTIVE;
	}

}
