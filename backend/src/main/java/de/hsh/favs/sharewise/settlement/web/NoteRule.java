package de.hsh.favs.sharewise.settlement.web;

/**
 * The one place for the text rules of a settlement note that Bean Validation cannot
 * express with a built-in annotation. Postgres refuses the character U+0000 in text, so
 * it must be a 400 BAD_REQUEST and not a 500.
 */
final class NoteRule {

	/** Any text without the NUL character; used with {@code @Pattern}. */
	static final String NO_NUL = "[^\\u0000]*";

	private NoteRule() {
	}

}
