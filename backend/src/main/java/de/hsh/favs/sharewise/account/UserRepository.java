package de.hsh.favs.sharewise.account;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<User, UUID> {

	List<User> findAllByOrderByDisplayNameAsc();

}
