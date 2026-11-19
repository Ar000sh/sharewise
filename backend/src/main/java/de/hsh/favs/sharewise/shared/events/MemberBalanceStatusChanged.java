package de.hsh.favs.sharewise.shared.events;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * A person's balance in a group became zero or stopped being zero. Published by the
 * settlement ledger only when the zero-state flips, consumed by the group to keep
 * {@code Membership.hasZeroBalance} up to date (R11: leave only at balance 0). Lives in
 * {@code shared.events} instead of {@code settlement.api} so that group does not depend
 * on settlement (D12).
 */
public record MemberBalanceStatusChanged(GroupId groupId, UserId userId, boolean hasZeroBalance, Instant occurredAt) {

}
