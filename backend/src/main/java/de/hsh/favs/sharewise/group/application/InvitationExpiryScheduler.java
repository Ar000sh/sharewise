package de.hsh.favs.sharewise.group.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically flips stored PENDING invitations past their 7-day validity to EXPIRED
 * (R9). Readers still derive expiry via {@code statusAt(now)} in between runs, so no
 * invitation is ever accepted after 7 days even before the next sweep.
 */
@Component
class InvitationExpiryScheduler {

	private static final Logger log = LoggerFactory.getLogger(InvitationExpiryScheduler.class);

	private final GroupService groups;

	InvitationExpiryScheduler(GroupService groups) {
		this.groups = groups;
	}

	@Scheduled(fixedDelayString = "${sharewise.invitations.expiry-check-interval:PT1H}")
	void expirePendingInvitations() {
		int expired = groups.expirePendingInvitations();
		if (expired > 0) {
			log.info("Marked {} pending invitation(s) as EXPIRED", expired);
		}
	}

}
