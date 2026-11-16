package io.github.mighil31.kitchensink.member;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Errors raised before a controller is chosen (unknown path, wrong method, unsupported media type),
 * which {@link MemberApiExceptionHandler} never sees. Under {@code /rest/} they become ProblemDetail;
 * elsewhere they are rethrown, so the UI keeps its HTML error page.
 */
@RestControllerAdvice
class ApiRoutingExceptionHandler {

    /** 405, 415 and 406, keeping Spring's own body and headers ({@code Allow}, {@code Accept}). */
    @ExceptionHandler({
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpMediaTypeNotAcceptableException.class
    })
    ResponseEntity<ProblemDetail> requestNotSupported(Exception ex, HttpServletRequest request) throws Exception {
        if (!isApi(request)) {
            throw ex;
        }
        ErrorResponse error = (ErrorResponse) ex;
        return ResponseEntity.status(error.getStatusCode())
                .headers(error.getHeaders())
                .body(error.getBody());
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
