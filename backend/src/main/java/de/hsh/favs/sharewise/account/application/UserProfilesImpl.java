package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.account.persistence.AccountRepository;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserProfilesImpl implements UserProfiles {

	private final AccountRepository accounts;

	UserProfilesImpl(AccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<UserProfile> find(UserId userId) {
		return accounts.findById(userId.value()).map(AccountService::toProfile);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserProfile> findProfiles(Set<UserId> userIds) {
		return accounts.findAllById(userIds.stream().map(UserId::value).toList())
			.stream()
			.map(AccountService::toProfile)
			.toList();
	}

}
