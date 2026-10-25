package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.Map;
import java.util.UUID;

/** Port: where the balance read model lives. */
interface BalanceStore {

	/** Remembers the expense as applied. Returns false when it was already applied. */
	boolean markProcessed(UUID expenseId);

	void add(GroupId group, MemberId member, long deltaCents);

	/** Only members that have a stored balance. */
	Map<MemberId, Long> balancesOf(GroupId group);

}
