package de.hsh.favs.sharewise.account.api;

/**
 * Everything the account context announces. Sign-in belongs to the identity provider
 * (Keycloak), so there are no login events here.
 */
public sealed interface AccountEvent permits AccountRegistered {

}
