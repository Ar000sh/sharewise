package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import java.util.Map;
import java.util.Set;

/**
 * Read access to what other contexts need to show about a group. Works without a
 * transaction.
 */
public interface GroupDirectory {

	/**
	 * The name of each of the given groups. Ids that belong to no group are left out; an
	 * empty set gives an empty map.
	 */
	Map<GroupId, String> findNames(Set<GroupId> groupIds);

}
