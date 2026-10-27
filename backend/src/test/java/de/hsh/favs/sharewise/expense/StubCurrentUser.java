package de.hsh.favs.sharewise.expense;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.UUID;

/** Caller is Alice unless a test switches it. */
public class StubCurrentUser implements CurrentUser {

	public static final UserProfile ALICE = new UserProfile(
			new UserId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")), "Alice", true);

	private UserProfile current = ALICE;

	public void as(UserProfile user) {
		this.current = user;
	}

	public void reset() {
		this.current = ALICE;
	}

	@Override
	public UserProfile get() {
		return current;
	}

}
