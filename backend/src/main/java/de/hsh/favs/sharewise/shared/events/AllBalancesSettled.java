package de.hsh.favs.sharewise.shared.events;

import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Instant;

/**
 * Every balance in a group is zero again after at least one was not. Published by the
 * settlement ledger; the group archives itself when it is closed for settlement (L5,
 * design 2026-11-28-group-lifecycle). Lives in {@code shared.events} like
 * {@link MemberBalanceStatusChanged} (D12).
 */
public record AllBalancesSettled(GroupId groupId, Instant occurredAt) {

}
