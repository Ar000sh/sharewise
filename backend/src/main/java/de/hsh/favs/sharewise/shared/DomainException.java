package de.hsh.favs.sharewise.shared;

/** A business rule or access rule was violated. Rendered as problem+json by the web layer. */
public class DomainException extends RuntimeException {

	private final ErrorCode code;

	public DomainException(ErrorCode code, String detail) {
		super(detail);
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}

}
