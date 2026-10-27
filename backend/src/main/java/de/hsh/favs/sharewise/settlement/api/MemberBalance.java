package de.hsh.favs.sharewise.settlement.api;

import de.hsh.favs.sharewise.shared.UserId;

public record MemberBalance(UserId memberId, String displayName, long balanceCents) {
}
