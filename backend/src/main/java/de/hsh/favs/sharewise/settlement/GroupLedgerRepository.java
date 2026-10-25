package de.hsh.favs.sharewise.settlement;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupLedgerRepository extends JpaRepository<GroupLedger, UUID> {

	/** Locked, so two events for the same group are applied one after the other. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from GroupLedger l where l.groupId = :groupId")
	Optional<GroupLedger> findForUpdate(@Param("groupId") UUID groupId);

	/** Race-free "create on first event": two first events cannot both insert the same ledger. */
	@Modifying
	@Query(value = "INSERT INTO settlement_ledger (group_id) VALUES (:groupId) ON CONFLICT DO NOTHING",
			nativeQuery = true)
	void createIfAbsent(@Param("groupId") UUID groupId);

}
