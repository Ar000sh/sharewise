/**
 * Notification: the inbox of things that happened to a person (generic subdomain). A leaf
 * module: it consumes events of other contexts and nothing may depend on it, so it has no
 * {@code api} package. Layout: domain, application, persistence, web.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "account::api", "group::api", "settlement::api", "shared" })
package de.hsh.favs.sharewise.notification;
