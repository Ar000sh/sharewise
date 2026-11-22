package de.hsh.favs.sharewise.group.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable closure snapshot, owned and created only by Group. */
@Entity
@Table(name = "group_archives")
public class GroupArchive {

	@Id
	private UUID id;

	@Column(name = "archive_version", nullable = false, updatable = false)
	private int version;

	@Column(name = "closed_by", nullable = false, updatable = false)
	private UUID closedBy;

	@Column(name = "closed_at", nullable = false, updatable = false)
	private Instant closedAt;

	@Column(nullable = false, updatable = false, columnDefinition = "text")
	private String snapshot;

	protected GroupArchive() {
	}

	GroupArchive(int version, UUID actor, Instant now, String snapshot) {
		this.id = UUID.randomUUID();
		this.version = version;
		this.closedBy = actor;
		this.closedAt = now;
		this.snapshot = snapshot;
	}

	public int version() {
		return version;
	}

	public UUID closedBy() {
		return closedBy;
	}

	public Instant closedAt() {
		return closedAt;
	}

	public String snapshot() {
		return snapshot;
	}

}
