package de.hsh.favs.sharewise.account.persistence;

import de.hsh.favs.sharewise.account.domain.Account;
import java.time.Instant;
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
		return insertIfAbsent(account.userId().value(), account.email().value(), account.displayName().value(),
				account.status().name(), account.registeredAt()) == 1;
	}

	@Modifying
	@Query(value = """
			INSERT INTO accounts (id, email, display_name, status, registered_at)
			VALUES (:id, :email, :displayName, :status, :registeredAt)
			ON CONFLICT DO NOTHING""", nativeQuery = true)
	int insertIfAbsent(@Param("id") UUID id, @Param("email") String email, @Param("displayName") String displayName,
			@Param("status") String status, @Param("registeredAt") Instant registeredAt);

}
