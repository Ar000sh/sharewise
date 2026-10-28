package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.domain.GroupMembershipView;
import de.hsh.favs.sharewise.expense.persistence.MembershipCopyRepository;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * What the expense context currently knows about a group, read from its own copy. The
 * copy is fed by events, so it can be a few milliseconds behind: Bob accepts an
 * invitation and records an expense straight away, and his MemberJoined has not been
 * applied yet. {@link #awaiting} therefore waits a short, bounded time for the people a
 * call needs before it gives up and returns what it has; the rules of the aggregate then
 * refuse.
 */
@Component
class KnownMemberships {

	private final MembershipCopyRepository copy;

	private final int attempts;

	private final Duration pause;

	KnownMemberships(MembershipCopyRepository copy,
			@Value("${sharewise.expense.membership-wait.attempts:6}") int attempts,
			@Value("${sharewise.expense.membership-wait.pause:100ms}") Duration pause) {
		this.copy = copy;
		this.attempts = attempts;
		this.pause = pause;
	}

	/**
	 * The group as known now, once every one of {@code required} is an active member of
	 * it, or as known after the last attempt. Empty when the group itself is still
	 * unknown. A person who really is no member costs the whole wait (attempts times
	 * pause) before the refusal.
	 */
	Optional<GroupMembershipView> awaiting(GroupId group, Collection<UserId> required) {
		Optional<GroupMembershipView> view = copy.find(group);
		for (int attempt = 1; attempt < attempts && !complete(view, required); attempt++) {
			if (!sleep()) {
				break;
			}
			view = copy.find(group);
		}
		return view;
	}

	private static boolean complete(Optional<GroupMembershipView> view, Collection<UserId> required) {
		return view.isPresent() && required.stream().allMatch(view.get()::isActiveMember);
	}

	private boolean sleep() {
		if (pause.isZero()) {
			return true;
		}
		try {
			Thread.sleep(pause);
			return true;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
