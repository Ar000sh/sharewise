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
	 * The profiles of the given accounts; ids without an account are left out. Order is
	 * not defined.
	 */
	List<UserProfile> findProfiles(Set<UserId> userIds);

}
