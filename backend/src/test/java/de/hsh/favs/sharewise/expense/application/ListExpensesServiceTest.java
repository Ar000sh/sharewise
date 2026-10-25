package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.adapter.persistence.ExpenseRepository;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.ExpenseFixture;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListExpensesServiceTest {

	@Mock GroupMembership groups;
	@Mock ExpenseRepository repository;

	GroupId group = new GroupId(UUID.randomUUID());

	@Test
	void returnsTheGroupsExpensesAfterTheMemberCheck() {
		// given
		List<Expense> stored = List.of(new ExpenseFixture().build());
		when(repository.findByGroupIdOrderByDateDescCreatedAtDesc(group.value())).thenReturn(stored);

		// when
		var result = new ListExpensesService(groups, repository).list(group, ALICE);

		// then
		assertThat(result).isEqualTo(stored);
	}

	@Test
	void nonMemberGetsNoExpenses() {
		// given
		doThrow(new DomainException(ErrorCode.NOT_A_MEMBER, "no")).when(groups).requireMember(group, ALICE);

		// when / then
		assertThatThrownBy(() -> new ListExpensesService(groups, repository).list(group, ALICE))
				.isInstanceOf(DomainException.class);
		verifyNoInteractions(repository);
	}

}
