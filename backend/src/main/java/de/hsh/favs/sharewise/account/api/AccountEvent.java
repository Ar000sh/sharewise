package de.hsh.favs.sharewise.account.api;

/** Everything the account context announces. */
public sealed interface AccountEvent permits AccountRegistered, LoginSucceeded, LoginFailed {

}
