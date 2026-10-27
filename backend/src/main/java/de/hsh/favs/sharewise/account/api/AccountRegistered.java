package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/** A new account exists and can sign in. */
public record AccountRegistered(UserId userId, Email email, Instant occurredAt) implements AccountEvent {

}
