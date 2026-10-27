package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.shared.Email;

/**
 * Sign in. The password is plain text here because a too short one must answer "wrong",
 * not "invalid".
 */
public record LoginCommand(Email email, String password) {

}
