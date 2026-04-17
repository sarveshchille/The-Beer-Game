package com.beergame.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts all service-layer exceptions into structured JSON responses.
 *
 * Before this class, every RuntimeException produced an unstructured HTTP 500
 * with a raw stack trace visible to the client — a security and UX problem.
 *
 * Response shape:
 * {
 *   "timestamp": "2025-01-01T12:00:00",
 *   "status":    409,
 *   "error":     "Conflict",
 *   "message":   "Role RETAILER is already taken in game ABC123"
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Domain exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(UserNameAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUsernameConflict(UserNameAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmailIdAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailConflict(EmailIdAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UserDoesNotExistException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserDoesNotExistException ex) {
        return build(HttpStatus.NOT_FOUND, "User not found");
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        // Never echo the raw message — it can reveal whether username or password is wrong.
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    // ── Game-logic RuntimeExceptions ──────────────────────────────────────────
    // These are thrown with descriptive messages from the service layer.

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Catches RuntimeExceptions thrown by the game services:
     *   "Game not found: XYZ"        → 404
     *   "Role X already taken"        → 409
     *   "Room is not running"         → 409
     *   "Player already submitted"    → 409
     *   anything else                 → 400
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred";

        if (msg.contains("not found")) {
            return build(HttpStatus.NOT_FOUND, msg);
        }
        if (msg.contains("already taken")
                || msg.contains("already in")
                || msg.contains("already submitted")
                || msg.contains("not running")
                || msg.contains("already running")) {
            return build(HttpStatus.CONFLICT, msg);
        }

        // Log unexpected runtime exceptions with stack trace for debugging
        log.error("Unhandled RuntimeException: {}", msg, ex);
        return build(HttpStatus.BAD_REQUEST, msg);
    }

    // ── Optimistic locking ────────────────────────────────────────────────────

    /**
     * Thrown when two concurrent advanceTurn() calls try to commit the same
     * Game @Version. The second request gets a 409 so the client can retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock collision: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Game state changed concurrently — please retry");
    }

    // ── Validation (@Valid) ───────────────────────────────────────────────────

    /**
     * Triggered when a request body fails @Valid Bean Validation.
     * Returns a map of field → error message so the frontend can highlight
     * specific form fields.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a   // keep first error per field if there are multiple
                ));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    HttpStatus.BAD_REQUEST.value());
        body.put("error",     "Validation Failed");
        body.put("fields",    fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        return ResponseEntity.status(status).body(body);
    }
}
