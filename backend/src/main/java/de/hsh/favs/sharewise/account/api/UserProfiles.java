package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read access to the profiles of accounts. Works without a transaction. */
public interface UserProfiles {

	/** The profile of one account, empty when the id belongs to no account. */
	Optional<UserProfile> find(UserId userId);

	/**
	 * The active account an exact username or email belongs to. The text is trimmed and
	 * compared in lower case; one containing {@code @} is an email, any other a username.
	 * There is no partial matching, so a blank text, a wildcard character or a prefix
	 * finds nobody. Empty for an unknown identifier and for an account that is not
	 * active. Works without a transaction.
	 */
	Optional<UserProfile> findByIdentifier(String identifier);

	/**
	 * The profiles of the given accounts; ids without an account are left out. Order is
	 * not defined.
	 */
	List<UserProfile> findProfiles(Set<UserId> userIds);

}
