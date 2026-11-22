package de.hsh.favs.sharewise.group.application;

import de.hsh.favs.sharewise.group.api.*;
import de.hsh.favs.sharewise.group.domain.*;
import de.hsh.favs.sharewise.group.persistence.GroupRepository;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.shared.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Coordinates closure and immutable snapshot persistence in one database transaction. */
@Service
public class GroupArchiveService {

	public record Member(UUID userId, String displayName, GroupRole role) {
	}

	public record Snapshot(UUID groupId, String name, String currency, List<Member> members,
			GroupFinancialHistory.Snapshot finances) {
	}

	public record Archive(int version, UUID closedBy, Instant closedAt, Snapshot snapshot) {
	}

	public record Entry(UUID id, String action, UUID actor, String reason, Instant occurredAt) {
	}

	public record Lifecycle(GroupStatus status, long revision, int archiveCount, List<Entry> history) {
	}

	private final GroupRepository groups;

	private final GroupFinancialHistory finances;

	private final UserProfiles profiles;

	private final JsonMapper json;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	public GroupArchiveService(GroupRepository groups, GroupFinancialHistory finances, UserProfiles profiles,
			JsonMapper json, Clock clock, ApplicationEventPublisher events) {
		this.groups = groups;
		this.finances = finances;
		this.profiles = profiles;
		this.json = json;
		this.clock = clock;
		this.events = events;
	}

	@Transactional
	public Lifecycle closeGroup(GroupId id, UserId caller, long expected) {
		Group group = locked(id);
		group.requireLifecycle(caller, expected, GroupStatus.OPEN);
		var financial = finances.capture(id);
		Map<UUID, String> names = new HashMap<>();
		Set<UserId> people = new HashSet<>();
		group.memberships().forEach(m -> people.add(m.userId()));
		financial.balances().forEach(b -> people.add(new UserId(b.userId())));
		profiles.findProfiles(people).forEach(p -> names.put(p.userId().value(), p.displayName()));
		var members = group.memberships()
			.stream()
			.map(m -> new Member(m.userId().value(), names.getOrDefault(m.userId().value(), "Former member"), m.role()))
			.toList();
		var snapshot = new Snapshot(id.value(), group.name().value(), group.currency().getCurrencyCode(), members,
				financial);
		boolean zero = financial.balances().stream().allMatch(b -> new BigDecimal(b.netBalance()).signum() == 0);
		var event = group.closeGroup(caller, expected, zero, json.writeValueAsString(snapshot), clock.instant());
		groups.saveAndFlush(group);
		events.publishEvent(event);
		return view(group);
	}

	@Transactional
	public Lifecycle reopenGroup(GroupId id, UserId caller, long expected, String reason) {
		Group group = locked(id);
		var event = group.reopenGroup(caller, expected, reason, clock.instant());
		groups.saveAndFlush(group);
		events.publishEvent(event);
		return view(group);
	}

	@Transactional(readOnly = true)
	public Lifecycle getLifecycle(GroupId id, UserId caller) {
		return view(readable(id, caller));
	}

	@Transactional(readOnly = true)
	public List<Archive> listArchives(GroupId id, UserId caller) {
		return readable(id, caller).archives()
			.stream()
			.map(a -> new Archive(a.version(), a.closedBy(), a.closedAt(),
					json.readValue(a.snapshot(), Snapshot.class)))
			.toList();
	}

	private Group locked(GroupId id) {
		return groups.findByIdForUpdate(id.value())
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
	}

	private Group readable(GroupId id, UserId caller) {
		Group group = groups.findById(id.value())
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
		if (!group.hasActiveMember(caller))
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group");
		return group;
	}

	private static Lifecycle view(Group g) {
		return new Lifecycle(g.status(), g.lifecycleRevision(), g.archives().size(),
				g.lifecycleEntries()
					.stream()
					.map(e -> new Entry(e.id(), e.action(), e.actor(), e.reason(), e.occurredAt()))
					.toList());
	}

}
