package de.hsh.favs.sharewise.settlement.web;

import java.util.regex.Pattern;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Reads an amount only from a JSON string of exactly two decimals. Jackson would
 * otherwise turn the JSON number {@code 10.5} or {@code 1e1} into text, and money must
 * never pass through a number (document 01, section 7). Anything but a string is a 400
 * BAD_REQUEST.
 */
class AmountText extends StdDeserializer<String> {

	/**
	 * The contract's Amount pattern, tightened to no leading zeros: an optional minus,
	 * digits without a leading zero, a point, exactly two decimals. {@code Money} stays
	 * lenient because other modules parse looser input.
	 */
	private static final Pattern TWO_DECIMALS = Pattern.compile("-?(0|[1-9][0-9]*)\\.[0-9]{2}");

	AmountText() {
		super(String.class);
	}

	@Override
	public String deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() == JsonToken.VALUE_STRING) {
			String text = parser.getString();
			if (!TWO_DECIMALS.matcher(text).matches()) {
				return (String) context.handleWeirdStringValue(String.class, text,
						"An amount has exactly two decimals, such as 90.00");
			}
			return text;
		}
		return (String) context.handleUnexpectedToken(String.class, parser);
	}

}
