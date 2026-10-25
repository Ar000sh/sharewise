package de.hsh.favs.sharewise.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.account.api.UserInfo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectoryImplTest {

	@Mock
	UserRepository users;

	@InjectMocks
	UserDirectoryImpl directory;

	@Test
	void findMapsTheStoredUserToUserInfo() {
		// given
		UserFixture bob = UserFixture.bob();
		when(users.findById(bob.id())).thenReturn(Optional.of(bob.build()));

		// when / then
		assertThat(directory.find(bob.userId())).contains(bob.info());
	}

	@Test
	void findIsEmptyForAnUnknownUser() {
		// given
		UserFixture unknown = UserFixture.alice().withId(UserFixture.unknownId());
		when(users.findById(unknown.id())).thenReturn(Optional.empty());

		// when / then
		assertThat(directory.find(unknown.userId())).isEmpty();
	}

	@Test
	void allReturnsEveryUserSortedByNameFromTheRepository() {
		// given
		when(users.findAllByOrderByDisplayNameAsc())
				.thenReturn(List.of(UserFixture.alice().build(), UserFixture.bob().build()));

		// when / then
		assertThat(directory.all()).containsExactly(UserFixture.alice().info(), UserFixture.bob().info());
	}

}
