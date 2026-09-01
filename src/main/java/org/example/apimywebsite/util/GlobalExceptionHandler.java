package org.example.apimywebsite.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // More specific than handleRuntimeException below, so Spring selects this one for
    // ResponseStatusException (a RuntimeException subtype) instead of falling through to
    // the generic handler, which previously discarded the exception's own intended status
    // (401/403/404/etc.) and always returned 400.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        // getReason() is the plain, developer-supplied string (e.g. "User not found") with
        // no status code or internal detail baked in — unlike getMessage(), which prepends
        // the status code. Falls back to a generic, safe message if no reason was supplied.
        String reason = ex.getReason();
        error.put("message", (reason != null && !reason.isBlank()) ? reason : "Request could not be completed.");
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    // More specific than handleRuntimeException below (DataIntegrityViolationException is a
    // RuntimeException subtype), so Spring selects this one instead. Without it, a DB-level
    // unique-constraint race (e.g. two concurrent registrations for the same email) falls
    // through to the generic handler below, which would return the raw Hibernate/JDBC
    // message verbatim — exposing the table name, constraint/index name, and the offending
    // value. ex is still logged server-side for diagnosis; only a fixed, safe message is
    // ever returned to the client.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        Map<String, String> error = new HashMap<>();
        error.put("message", "A conflicting value already exists.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Upload-size-limit finding: Spring throws MaxUploadSizeExceededException while parsing
    // the multipart request - before any controller/service/Cloudinary code runs - whenever
    // a part exceeds spring.servlet.multipart.max-file-size or the whole request exceeds
    // max-request-size (both configured in application.properties). It is a
    // MultipartException/RuntimeException subtype whose own getMessage() embeds the
    // configured byte limit (e.g. "Maximum upload size of 5242880 bytes exceeded") and, for
    // some multipart parser implementations, the underlying parser's exception class name.
    // More specific than handleRuntimeException below, so Spring selects this one instead of
    // letting that raw text leak through at the wrong status (400 instead of 413). ex is
    // logged server-side only; the client always gets the same fixed, safe message.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("Upload rejected: exceeded configured multipart size limit", ex);
        Map<String, String> error = new HashMap<>();
        error.put("message", "Uploaded file is too large.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    // H8 fix: this is the last-resort catch-all for any RuntimeException not handled by a more
    // specific @ExceptionHandler above (ResponseStatusException, DataIntegrityViolationException,
    // MaxUploadSizeExceededException all take precedence via Spring's most-specific-match
    // dispatch). Every currently-known business-logic exception in this codebase already throws
    // a typed ResponseStatusException with its own intended status/message, so anything landing
    // here is a genuinely unexpected failure (NPE, bug, etc.) - ex.getMessage() must never be
    // echoed to the client, and it is not a "not found"/"forbidden" case, so 500 (not 400) is the
    // correct status. ex is still logged server-side, with its full Throwable, for diagnosis.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled RuntimeException", ex);
        Map<String, String> error = new HashMap<>();
        error.put("message", "An unexpected error occurred.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(errors);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleParsingError(HttpMessageNotReadableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "Invalid request format");
        return ResponseEntity.badRequest().body(error);
    }

}
