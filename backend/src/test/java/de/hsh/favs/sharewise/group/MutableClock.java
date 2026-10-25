package de.hsh.favs.sharewise.group;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward, used to test invitation expiry. */
public class MutableClock extends Clock {

	public static final Instant START = Instant.parse("2027-06-01T10:00:00Z");

	private volatile Instant now = START;

	public void reset() {
		now = START;
	}

	public void advance(Duration d) {
		now = now.plus(d);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return now;
	}

}
