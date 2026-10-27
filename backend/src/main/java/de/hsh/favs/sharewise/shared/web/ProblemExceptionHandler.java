package de.hsh.favs.sharewise.shared.web;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps exceptions to RFC 7807 problem+json with the stable "code" property of the API
 * contract.
 */
@RestControllerAdvice
public class ProblemExceptionHandler {

	@ExceptionHandler(DomainException.class)
	ProblemDetail onDomain(DomainException e) {
		return problem(e.code(), e.getMessage());
	}

	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
			MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
			ConstraintViolationException.class })
	ProblemDetail onBadRequest(Exception e) {
		return problem(ErrorCode.BAD_REQUEST, "Malformed or invalid request");
	}

	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code.status()), detail);
		pd.setProperty("code", code.name());
		return pd;
	}

}
