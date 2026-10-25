package de.hsh.favs.sharewise.shared;

/** Stable error codes of the API contract (docs/api/openapi.yaml) with their HTTP status. */
public enum ErrorCode {

	BAD_REQUEST(400),
	UNAUTHENTICATED(401),
	NOT_A_MEMBER(403),
	GROUP_NOT_FOUND(404),
	INVITATION_NOT_FOUND(404),
	ALREADY_MEMBER(409),
	INVITATION_EXPIRED(410),
	AMOUNT_NOT_POSITIVE(422),
	PAYER_NOT_IN_GROUP(422),
	PARTICIPANT_NOT_IN_GROUP(422),
	NO_PARTICIPANTS(422),
	GROUP_NOT_OPEN(422),
	SPLIT_NOT_SUPPORTED(422),
	EMAIL_TAKEN(409),
	INVALID_CREDENTIALS(401);

	private final int status;

	ErrorCode(int status) {
		this.status = status;
	}

	public int status() {
		return status;
	}

}
