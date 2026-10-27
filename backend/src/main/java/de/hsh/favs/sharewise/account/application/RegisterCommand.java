package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.account.domain.DisplayName;
import de.hsh.favs.sharewise.account.domain.RawPassword;
import de.hsh.favs.sharewise.shared.Email;

/** Sign up with an email, a display name and a password. */
public record RegisterCommand(Email email, DisplayName displayName, RawPassword password) {

}
