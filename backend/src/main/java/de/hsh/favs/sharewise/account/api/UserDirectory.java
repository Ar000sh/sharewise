package de.hsh.favs.sharewise.account.api;

import de.hsh.favs.sharewise.shared.UserId;
import java.util.Optional;

public interface UserDirectory {

	Optional<UserInfo> find(UserId id);

}
