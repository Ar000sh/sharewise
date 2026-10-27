/**
 * Settlement: balances and later settlement payments (core subdomain). Layout: api,
 * domain, application, persistence, web.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "account::api", "group::api", "expense::api", "shared" })
package de.hsh.favs.sharewise.settlement;
