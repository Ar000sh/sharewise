package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/** A sign-in with a wrong password, or for an account that is not active. */
public record LoginFailed(UserId userId, Instant occurredAt) implements AccountEvent {

}
