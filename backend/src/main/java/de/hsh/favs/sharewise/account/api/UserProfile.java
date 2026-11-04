package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.UserId;

/**
 * What other contexts may know about a person: who it is, how to show it, the name people
 * type to find them, and whether the account can still be used. Display name and username
 * are plain text; the account's own value objects never leave the account module.
 */
public record UserProfile(UserId userId, String displayName, String username, boolean isActive) {

}
