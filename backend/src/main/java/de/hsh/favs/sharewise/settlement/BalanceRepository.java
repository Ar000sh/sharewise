package de.hsh.favs.sharewise.settlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BalanceRepository extends JpaRepository<BalanceEntry, BalanceEntry.Key> {

	List<BalanceEntry> findByIdGroupId(UUID groupId);

	/** Atomic, so concurrent events for the same member cannot lose an update. */
	@Modifying
	@Query(value = """
			INSERT INTO settlement_balance (group_id, member_id, balance_cents)
			VALUES (:groupId, :memberId, :delta)
			ON CONFLICT (group_id, member_id)
			DO UPDATE SET balance_cents = settlement_balance.balance_cents + :delta
			""", nativeQuery = true)
	void addDelta(@Param("groupId") UUID groupId, @Param("memberId") UUID memberId, @Param("delta") long delta);

	@Modifying
	@Query(value = "INSERT INTO settlement_processed_expense (expense_id) VALUES (:expenseId) ON CONFLICT DO NOTHING",
			nativeQuery = true)
	int markProcessed(@Param("expenseId") UUID expenseId);

}
