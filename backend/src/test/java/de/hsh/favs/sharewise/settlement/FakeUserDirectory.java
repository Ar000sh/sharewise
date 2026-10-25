package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.account.api.UserDirectory;
import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class FakeUserDirectory implements UserDirectory {

	private final Map<UserId, UserInfo> users = new ConcurrentHashMap<>();

	void add(UserId id, String displayName) {
		users.put(id, new UserInfo(id, displayName));
	}

	@Override
	public Optional<UserInfo> find(UserId id) {
		return Optional.ofNullable(users.get(id));
	}

	@Override
	public List<UserInfo> all() {
		return List.copyOf(users.values());
	}

}
