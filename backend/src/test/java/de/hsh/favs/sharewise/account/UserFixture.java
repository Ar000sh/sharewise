package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.UUID;

/**
 * Test data for a user. Defaults to Alice, the first user seeded by {@code db/migration-dev/V190}; {@link #bob()},
 * {@link #carol()} and {@link #david()} are the other seeded users. Override anything with {@code withX}.
 */
public class UserFixture {

	private UUID id = seededId(1);

	private String displayName = "Alice";

	public static UserFixture alice() {
		return new UserFixture();
	}

	public static UserFixture bob() {
		return new UserFixture().withId(seededId(2)).withDisplayName("Bob");
	}

	public static UserFixture carol() {
		return new UserFixture().withId(seededId(3)).withDisplayName("Carol");
	}

	public static UserFixture david() {
		return new UserFixture().withId(seededId(4)).withDisplayName("David");
	}

	/** A well-formed id that is not in the users table. */
	public static UUID unknownId() {
		return seededId(255);
	}

	public UserFixture withId(UUID id) {
		this.id = id;
		return this;
	}

	public UserFixture withDisplayName(String displayName) {
		this.displayName = displayName;
		return this;
	}

	public UUID id() {
		return id;
	}

	public String idString() {
		return id.toString();
	}

	public String displayName() {
		return displayName;
	}

	public UserId userId() {
		return new UserId(id);
	}

	public MemberId memberId() {
		return new MemberId(id);
	}

	public UserInfo info() {
		return new UserInfo(userId(), displayName);
	}

	User build() {
		return new User(id, displayName);
	}

	private static UUID seededId(int n) {
		return UUID.fromString("aaaaaaaa-0000-0000-0000-%012d".formatted(n));
	}

}
