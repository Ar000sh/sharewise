package de.hsh.favs.sharewise.shared.events;

import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Instant;

/**
 * Every balance in a group is zero again after at least one was not. Published by the
 * settlement ledger; it has no consumer yet: the group's reaction (auto-archive, L5) is
 * parked for the team. Lives in {@code shared.events} like
 * {@link MemberBalanceStatusChanged} (D12).
 */
public record AllBalancesSettled(GroupId groupId, Instant occurredAt) {

}
