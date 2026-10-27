/**
 * Group: groups, membership and invitations (supporting subdomain). Layout: api, domain,
 * application, persistence, web.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "account::api", "shared" })
package de.hsh.favs.sharewise.group;
