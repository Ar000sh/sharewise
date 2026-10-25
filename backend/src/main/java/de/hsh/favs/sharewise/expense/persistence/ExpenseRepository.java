package de.hsh.favs.sharewise.expense.persistence;

import de.hsh.favs.sharewise.expense.domain.Expense;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The only repository of the module, because Expense is the aggregate root. */
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

	/** Newest first: by date, then creation time. */
	List<Expense> findByGroupIdOrderByDateDescCreatedAtDesc(UUID groupId);

}
