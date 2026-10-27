package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import java.time.Instant;
import java.util.UUID;

/**
 * Test data for an {@link Invitation}. By default: token "token-1", issued by Alice at
 * {@link MutableClock#START}.
 */
class InvitationFixture {

	private String token = "token-1";

	private UUID createdBy = AccountFixture.alice().id();

	private Instant issuedAt = MutableClock.START;

	InvitationFixture withToken(String token) {
		this.token = token;
		return this;
	}

	InvitationFixture withCreatedBy(UUID createdBy) {
		this.createdBy = createdBy;
		return this;
	}

	InvitationFixture withIssuedAt(Instant issuedAt) {
		this.issuedAt = issuedAt;
		return this;
	}

	Invitation build() {
		return Invitation.issue(token, createdBy, issuedAt);
	}

}
