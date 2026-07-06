package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.time.Instant;

/**
 * Uniform error body returned by {@code GlobalExceptionHandler} (design.md —
 * REST endpoints): {@code {timestamp, status, error, message, path}}.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
