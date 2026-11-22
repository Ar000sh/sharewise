package de.hsh.favs.sharewise.shared.events;

import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Instant;

/**
 * Every balance in a group is zero again after at least one was not. Published by the
 * settlement ledger. This does not close the group: revised L5 creates an archive only
 * when an admin successfully closes the group. Lives in {@code shared.events} like
 * {@link MemberBalanceStatusChanged} (D12).
 */
public record AllBalancesSettled(GroupId groupId, Instant occurredAt) {

}
