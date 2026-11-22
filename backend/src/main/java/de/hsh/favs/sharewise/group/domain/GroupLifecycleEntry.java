package de.hsh.favs.sharewise.group.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** An immutable lifecycle decision, owned by Group. */
@Entity
@Table(name = "group_lifecycle_entries")
public class GroupLifecycleEntry {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private long revision;

	@Column(nullable = false, updatable = false)
	private String action;

	@Column(nullable = false, updatable = false)
	private UUID actor;

	@Column(nullable = false, updatable = false, length = 500)
	private String reason;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	protected GroupLifecycleEntry() {
	}

	GroupLifecycleEntry(long revision, String action, UUID actor, String reason, Instant now) {
		this.id = UUID.randomUUID();
		this.revision = revision;
		this.action = action;
		this.actor = actor;
		this.reason = reason;
		this.occurredAt = now;
	}

	public UUID id() {
		return id;
	}

	public String action() {
		return action;
	}

	public UUID actor() {
		return actor;
	}

	public String reason() {
		return reason;
	}

	public Instant occurredAt() {
		return occurredAt;
	}

}
