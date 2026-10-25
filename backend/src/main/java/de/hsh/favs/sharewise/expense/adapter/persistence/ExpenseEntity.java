package de.hsh.favs.sharewise.expense.adapter.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "expense")
class ExpenseEntity {

	@Id
	UUID id;

	@Column(name = "group_id", nullable = false)
	UUID groupId;

	@Column(nullable = false)
	String title;

	@Column(name = "amount_cents", nullable = false)
	long amountCents;

	@Column(name = "expense_date", nullable = false)
	LocalDate date;

	@Column(name = "payer_id", nullable = false)
	UUID payerId;

	@Column(name = "created_at", nullable = false)
	Instant createdAt;

	@ElementCollection
	@CollectionTable(name = "expense_share", joinColumns = @JoinColumn(name = "expense_id"))
	@OrderColumn(name = "position")
	List<ShareEntity> shares = new ArrayList<>();

	protected ExpenseEntity() {
	}

}
