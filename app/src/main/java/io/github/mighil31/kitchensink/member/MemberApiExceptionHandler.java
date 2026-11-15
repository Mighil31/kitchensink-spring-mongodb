package io.github.mighil31.kitchensink.member;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * API errors as RFC 9457 ProblemDetail (ADR 0003), the part the write path needs: validation 400
 * and duplicate email 409, each with an {@code errors} map of field to message, which keeps the
 * per-field detail legacy clients had. The base class turns Spring MVC's own errors (unreadable
 * body, unsupported media type) into ProblemDetail too. Catch-all 500 and trace ids: #18.
 */
@RestControllerAdvice(assignableTypes = MemberRestController.class)
class MemberApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setDetail("Validation failed");
        body.setProperty("errors", fieldErrors(ex));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @ExceptionHandler
    ProblemDetail emailTaken(EmailAlreadyRegisteredException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email taken");
        body.setProperty("errors", Map.of("email", "Email taken"));
        return body;
    }

    /**
     * One message per field, like legacy. A missing value breaks {@code @NotNull} and, for email,
     * {@code @NotEmpty} too; "must not be null" wins, as on legacy. Other fields breaking two rules
     * report one of them (gap B16, open).
     */
    private static Map<String, String> fieldErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getFieldErrors()) {
            if ("NotNull".equals(error.getCode()) || !errors.containsKey(error.getField())) {
                errors.put(error.getField(), error.getDefaultMessage());
            }
        }
        return errors;
    }
}
