package de.hsh.favs.sharewise.settlement.persistence;

import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupLedgerRepository extends JpaRepository<GroupLedger, UUID> {

	/** Locked, so two events for the same group are applied one after the other. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from GroupLedger l where l.groupId = :groupId")
	Optional<GroupLedger> findForUpdate(@Param("groupId") UUID groupId);

	/**
	 * Race-free "create on first event": two first events cannot both insert the same
	 * ledger.
	 */
	@Modifying
	@Query(value = "INSERT INTO settlement_ledger (group_id) VALUES (:groupId) ON CONFLICT DO NOTHING",
			nativeQuery = true)
	void createIfAbsent(@Param("groupId") UUID groupId);

	/** Whether this expense already has entries; asked after the lock is taken. */
	@Query("select count(e) > 0 from LedgerEntry e where e.ledger.groupId = :groupId and e.referenceId = :referenceId")
	boolean isBooked(@Param("groupId") UUID groupId, @Param("referenceId") UUID referenceId);

	/**
	 * The balance of every member that has entries, summed by the database instead of
	 * loading all entries.
	 */
	@Query("select e.memberId as memberId, sum(e.deltaCents) as cents from LedgerEntry e "
			+ "where e.ledger.groupId = :groupId group by e.memberId")
	List<MemberTotal> totals(@Param("groupId") UUID groupId);

	interface MemberTotal {

		UUID getMemberId();

		Long getCents();

	}

}
