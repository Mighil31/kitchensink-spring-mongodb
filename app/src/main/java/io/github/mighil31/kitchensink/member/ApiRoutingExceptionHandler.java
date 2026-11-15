package io.github.mighil31.kitchensink.member;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Errors that happen before any controller is chosen (unknown path, wrong method), so
 * {@link MemberApiExceptionHandler} never sees them. Under {@code /rest/} they become ProblemDetail
 * like every other API error (ADR 0003). Anywhere else the exception is rethrown and Spring's
 * default handling applies, so the UI keeps its HTML error page.
 */
@RestControllerAdvice
class ApiRoutingExceptionHandler {

    @ExceptionHandler
    ResponseEntity<ProblemDetail> methodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request)
            throws HttpRequestMethodNotSupportedException {
        if (!isApi(request)) {
            throw ex;
        }
        // Keeps the Allow header that lists the supported methods.
        return ResponseEntity.status(ex.getStatusCode())
                .headers(ex.getHeaders())
                .body(ex.getBody());
    }

    @ExceptionHandler
    ProblemDetail noSuchEndpoint(NoResourceFoundException ex, HttpServletRequest request)
            throws NoResourceFoundException {
        if (!isApi(request)) {
            throw ex;
        }
        // Spring's own detail says "No static resource ...", which describes its internals.
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such endpoint");
    }

    private static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/rest/");
    }
}
