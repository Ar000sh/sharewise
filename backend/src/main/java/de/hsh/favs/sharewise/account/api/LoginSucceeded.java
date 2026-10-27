package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/** The right password was given for an active account. */
public record LoginSucceeded(UserId userId, Instant occurredAt) implements AccountEvent {

}
