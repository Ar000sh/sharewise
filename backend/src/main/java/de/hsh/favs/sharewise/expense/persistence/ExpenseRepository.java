package de.hsh.favs.sharewise.expense.persistence;

import de.hsh.favs.sharewise.expense.domain.Expense;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The only repository of the module, because Expense is the aggregate root. */
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

	/** Newest first: by date, then creation time. */
	List<Expense> findByGroupIdOrderByDateDescCreatedAtDesc(UUID groupId);

	/**
	 * The expense, locked for the rest of the transaction, so two corrections or a
	 * correction and a removal at the same moment are serialized.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Expense e where e.id = :id and e.groupId = :groupId")
	Optional<Expense> findInGroupForUpdate(@Param("groupId") UUID groupId, @Param("id") UUID id);

}
