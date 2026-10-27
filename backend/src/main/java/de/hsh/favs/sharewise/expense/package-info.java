/**
 * Expense: recording and splitting expenses (core subdomain). Layout: api, domain,
 * application, persistence, web.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "account::api", "group::api", "shared" })
package de.hsh.favs.sharewise.expense;
