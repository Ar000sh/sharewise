package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.UserId;

/**
 * What other contexts may know about a person: who it is, how to show it, and whether the
 * account can still be used. The display name is plain text; the account's own value
 * objects never leave the account module.
 */
public record UserProfile(UserId userId, String displayName, boolean isActive) {

}
