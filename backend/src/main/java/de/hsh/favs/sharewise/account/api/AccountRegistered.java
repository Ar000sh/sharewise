package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * A new account exists: a person signed in at the identity provider for the first time
 * (D4).
 */
public record AccountRegistered(UserId userId, Email email, Instant occurredAt) implements AccountEvent {

}
