package de.hsh.favs.sharewise.settlement.api;

import de.hsh.favs.sharewise.shared.MemberId;

public record MemberBalance(MemberId memberId, String displayName, long balanceCents) {
}
