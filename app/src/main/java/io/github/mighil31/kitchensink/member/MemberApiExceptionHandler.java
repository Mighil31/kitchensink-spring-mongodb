package io.github.mighil31.kitchensink.member;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every API error as RFC 9457 ProblemDetail (ADR 0003): validation 400 and duplicate email 409 with
 * an {@code errors} map of field to message (the per-field detail legacy clients had), unknown
 * member 404, anything unexpected 500. The base class turns Spring MVC's own errors (unreadable
 * body, unsupported media type) into ProblemDetail too, with generic text: no parser or validator
 * internals reach the client (gaps B5, B6, B17).
 */
@RestControllerAdvice(assignableTypes = MemberRestController.class)
class MemberApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MemberApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setDetail("Validation failed");
        body.setProperty("errors", fieldErrors(ex));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Request parameters out of range ({@code ?size=1000}): 400 with the same {@code errors} map. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setDetail("Validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            result.getResolvableErrors().stream()
                    .findFirst()
                    .ifPresent(error ->
                            errors.put(result.getMethodParameter().getParameterName(), error.getDefaultMessage()));
        }
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @ExceptionHandler
    ProblemDetail emailTaken(EmailAlreadyRegisteredException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email taken");
        body.setProperty("errors", Map.of("email", "Email taken"));
        return body;
    }

    @ExceptionHandler
    ProblemDetail memberNotFound(MemberNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Member not found");
    }

    /**
     * The client gets a generic message and an id; the log gets the same id with the full
     * exception, so a reported error can be found without exposing internals.
     */
    @ExceptionHandler
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        log.error("Unexpected error on {} {} [traceId={}]", request.getMethod(), request.getRequestURI(), traceId, ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        body.setProperty("traceId", traceId);
        return body;
    }

    /** One message per field, as legacy clients expect, chosen in a fixed order (gap B16). */
    private static Map<String, String> fieldErrors(MethodArgumentNotValidException ex) {
        return FieldMessages.firstPerField(ex.getFieldErrors(), FieldError::getDefaultMessage);
    }
}
