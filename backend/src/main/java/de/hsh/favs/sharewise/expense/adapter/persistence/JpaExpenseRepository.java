package de.hsh.favs.sharewise.expense.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaExpenseRepository extends JpaRepository<ExpenseEntity, UUID> {

	List<ExpenseEntity> findByGroupIdOrderByDateDescCreatedAtDesc(UUID groupId);

}
