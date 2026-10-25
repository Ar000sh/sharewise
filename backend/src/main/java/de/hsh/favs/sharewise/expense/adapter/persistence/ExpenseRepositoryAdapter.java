package de.hsh.favs.sharewise.expense.adapter.persistence;

import de.hsh.favs.sharewise.expense.application.ExpenseRepositoryPort;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.Share;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class ExpenseRepositoryAdapter implements ExpenseRepositoryPort {

	private final JpaExpenseRepository jpa;

	ExpenseRepositoryAdapter(JpaExpenseRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public void save(Expense expense) {
		var entity = new ExpenseEntity();
		entity.id = expense.id();
		entity.groupId = expense.groupId().value();
		entity.title = expense.title();
		entity.amountCents = expense.amount().cents();
		entity.date = expense.date();
		entity.payerId = expense.payerId().value();
		entity.createdAt = expense.createdAt();
		expense.shares().forEach(s -> entity.shares.add(new ShareEntity(s.memberId().value(), s.amount().cents())));
		jpa.save(entity);
	}

	@Override
	public List<Expense> findByGroup(GroupId groupId) {
		return jpa.findByGroupIdOrderByDateDescCreatedAtDesc(groupId.value()).stream().map(this::toDomain).toList();
	}

	private Expense toDomain(ExpenseEntity e) {
		var shares = e.shares.stream()
				.map(s -> new Share(new MemberId(s.memberId), Money.eur(s.shareCents))).toList();
		return Expense.restore(e.id, new GroupId(e.groupId), e.title, Money.eur(e.amountCents), e.date,
				new MemberId(e.payerId), shares, e.createdAt);
	}

}
