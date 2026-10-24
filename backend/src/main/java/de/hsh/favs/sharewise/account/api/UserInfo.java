package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.UserId;

public record UserInfo(UserId id, String displayName) {
}
