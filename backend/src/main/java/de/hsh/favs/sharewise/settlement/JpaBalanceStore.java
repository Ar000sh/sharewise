package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Driven adapter for {@link BalanceStore}. */
@Component
class JpaBalanceStore implements BalanceStore {

	private final BalanceRepository repository;

	JpaBalanceStore(BalanceRepository repository) {
		this.repository = repository;
	}

	@Override
	public boolean markProcessed(UUID expenseId) {
		return repository.markProcessed(expenseId) == 1;
	}

	@Override
	public void add(GroupId group, MemberId member, long deltaCents) {
		repository.addDelta(group.value(), member.value(), deltaCents);
	}

	@Override
	public Map<MemberId, Long> balancesOf(GroupId group) {
		Map<MemberId, Long> balances = new HashMap<>();
		for (var entry : repository.findByIdGroupId(group.value())) {
			balances.put(new MemberId(entry.id().memberId()), entry.balanceCents());
		}
		return balances;
	}

}
