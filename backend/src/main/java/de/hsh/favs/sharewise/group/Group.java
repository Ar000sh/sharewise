package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.Role;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "groups")
class Group {

	/** Group currency is fixed to EUR in Ex4. */
	static final String CURRENCY = "EUR";

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GroupStatus status;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id", nullable = false)
	@OrderBy("joinedAt ASC, memberId ASC")
	private List<Member> members = new ArrayList<>();

	protected Group() {
	}

	/** A new OPEN group whose creator is its ADMIN. */
	static Group create(String name, UUID creator, Instant now) {
		Group g = new Group();
		g.id = UUID.randomUUID();
		g.name = name;
		g.status = GroupStatus.OPEN;
		g.currency = CURRENCY;
		g.createdAt = now;
		g.members.add(new Member(creator, Role.ADMIN, now));
		return g;
	}

	boolean hasMember(UUID memberId) {
		return members.stream().anyMatch(m -> m.memberId().equals(memberId));
	}

	void addMember(UUID memberId, Instant now) {
		members.add(new Member(memberId, Role.MEMBER, now));
	}

	UUID id() {
		return id;
	}

	String name() {
		return name;
	}

	GroupStatus status() {
		return status;
	}

	List<Member> members() {
		return List.copyOf(members);
	}

	int memberCount() {
		return members.size();
	}

}
