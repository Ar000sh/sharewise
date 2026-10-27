package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;
import java.util.ArrayList;
import java.util.List;

/**
 * Equal split (R4): total / n, remainder cents one each to the first participants in the
 * given order.
 */
public final class EqualSplit {

	private EqualSplit() {
	}

	public static List<Share> split(Money total, List<MemberId> participants) {
		int n = participants.size();
		long base = total.cents() / n;
		long remainder = total.cents() % n;
		var shares = new ArrayList<Share>(n);
		for (int i = 0; i < n; i++) {
			long cents = base + (i < remainder ? 1 : 0);
			shares.add(new Share(participants.get(i), new Money(cents, total.currency())));
		}
		return List.copyOf(shares);
	}

}
