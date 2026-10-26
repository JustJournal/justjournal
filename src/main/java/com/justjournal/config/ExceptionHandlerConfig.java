package com.justjournal.config;

import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@Slf4j
@Configuration
@ControllerAdvice
public class ExceptionHandlerConfig {
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException ex) {
        log.error("Unauthorized request: {}", ex.getMessage(), ex);
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * Anything not handled above. Spring's own request errors (406 not acceptable, 405 wrong
     * method, 415 unsupported content type, a missing parameter, ...) and exceptions annotated
     * with {@link ResponseStatus} keep their status; only real failures become a 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralError(Exception ex) {
        final HttpStatusCode status = statusOf(ex);
        if (status != null && status.is4xxClientError()) {
            log.debug("{} error: {}", status.value(), ex.getMessage());
            final HttpHeaders headers =
                    ex instanceof ErrorResponse errorResponse ? errorResponse.getHeaders() : HttpHeaders.EMPTY;
            return ResponseEntity.status(status)
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorHandler.modelError(reason(status)));
        }
        log.error("500 error: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unknown error occurred. Please try again later.");
    }

    private static HttpStatusCode statusOf(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode();
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return HttpStatus.BAD_REQUEST;
        }
        final ResponseStatus responseStatus =
                AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        return responseStatus == null ? null : responseStatus.code();
    }

    private static String reason(HttpStatusCode status) {
        final HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? "Request error" : known.getReasonPhrase();
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Unmapped URLs (bot probes, old .jsp links) are a 404, not a server error. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Not found");
    }

    /** e.g. /users/{username}/{year} hit with a non-numeric year. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return error(HttpStatus.NOT_FOUND, "Not found");
        }
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter " + ex.getName());
    }

    /** An upload over the size limit, whether Spring's multipart limit or a controller's own. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(MaxUploadSizeExceededException ex) {
        final long max = ex.getMaxUploadSize();
        return error(HttpStatus.PAYLOAD_TOO_LARGE,
                max > 0 ? "The file is too large. The limit is " + (max / (1024 * 1024)) + " MB."
                        : "The file is too large.");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Always send the error body as JSON. Without an explicit type, a handler that
     * produces text/html (or a failed Thymeleaf render) leaves a preset Content-Type that
     * no converter can write a Map to, which turns the error into a second 500.
     */
    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorHandler.modelError(message));
    }
}
