package de.hsh.favs.sharewise.settlement.api;

import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;

public interface Balances {

	/**
	 * Every current member of the group, 0 when they have no expenses. The sum is always
	 * 0.
	 */
	List<MemberBalance> of(GroupId group);

}
