package de.hsh.favs.sharewise.account.persistence;

import de.hsh.favs.sharewise.account.domain.Account;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	/**
	 * Stores a new account unless a row with its id or its email already exists. Two
	 * first requests of the same person can arrive together (F1): the database decides
	 * which one inserts, and the other one waits for that commit instead of failing.
	 * Needs a running transaction.
	 * @return true when this call inserted the row
	 */
	default boolean insertIfAbsent(Account account) {
		return insertIfAbsent(account.userId().value(), account.email().value(), account.username().value(),
				account.displayName().value(), account.status().name(), account.registeredAt()) == 1;
	}

	@Modifying
	@Query(value = """
			INSERT INTO accounts (id, email, username, display_name, status, registered_at)
			VALUES (:id, :email, :username, :displayName, :status, :registeredAt)
			ON CONFLICT DO NOTHING""", nativeQuery = true)
	int insertIfAbsent(@Param("id") UUID id, @Param("email") String email, @Param("username") String username,
			@Param("displayName") String displayName, @Param("status") String status,
			@Param("registeredAt") Instant registeredAt);

	/** Exact match on the stored (lower case) username. */
	Optional<Account> findByUsername(String username);

	/** Exact match on the stored (lower case) email. */
	Optional<Account> findByEmail(String email);

}
