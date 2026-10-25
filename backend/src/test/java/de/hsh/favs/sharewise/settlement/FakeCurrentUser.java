package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;

class FakeCurrentUser implements CurrentUser {

	private volatile UserInfo user;

	void actAs(UserInfo user) {
		this.user = user;
	}

	@Override
	public UserInfo get() {
		if (user == null) {
			throw new DomainException(ErrorCode.UNAUTHENTICATED, "No caller");
		}
		return user;
	}

}
