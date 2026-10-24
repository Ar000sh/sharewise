/** Settlement: balances and later settlement payments (core subdomain). Hexagonal. */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "account::api", "group::api", "expense::api", "shared" })
package de.hsh.favs.sharewise.settlement;
