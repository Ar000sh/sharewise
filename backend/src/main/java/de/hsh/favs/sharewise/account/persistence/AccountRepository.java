package de.hsh.favs.sharewise.account.persistence;

import de.hsh.favs.sharewise.account.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByEmail(String email);

	boolean existsByEmail(String email);

}
