package de.hsh.favs.sharewise.settlement.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Reads an amount only from a JSON string. Jackson would otherwise turn the JSON number
 * {@code 10.5} or {@code 1e1} into text, and money must never pass through a number
 * (document 01, section 7). Anything but a string is a 400 BAD_REQUEST.
 */
class AmountText extends StdDeserializer<String> {

	AmountText() {
		super(String.class);
	}

	@Override
	public String deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() == JsonToken.VALUE_STRING) {
			return parser.getString();
		}
		return (String) context.handleUnexpectedToken(String.class, parser);
	}

}
