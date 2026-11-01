package de.hsh.favs.sharewise.account.application;

/**
 * The claims of a validated Keycloak access token that the account context uses: the
 * subject (becomes the {@code UserId}), the email and the two name claims of C7. Plain
 * text, because the token is not trusted to be well-formed beyond its signature.
 */
record IdentityClaims(String subject, String email, String name, String preferredUsername) {

}
