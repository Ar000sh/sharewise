/** Expense: recording and splitting expenses (core subdomain). Hexagonal. */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "account::api", "group::api", "shared" })
package de.hsh.favs.sharewise.expense;
