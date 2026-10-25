package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.UserDirectory;
import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class UserDirectoryImpl implements UserDirectory {

	private final UserRepository users;

	UserDirectoryImpl(UserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<UserInfo> find(UserId id) {
		return users.findById(id.value()).map(UserDirectoryImpl::toInfo);
	}

	@Override
	public List<UserInfo> all() {
		return users.findAllByOrderByDisplayNameAsc().stream().map(UserDirectoryImpl::toInfo).toList();
	}

	private static UserInfo toInfo(User u) {
		return new UserInfo(new UserId(u.id()), u.displayName());
	}

}
