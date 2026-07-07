package mx.edu.utez.sisa.shared.web.dto;

import java.time.Instant;

/**
 * Uniform error body returned by every module's {@code GlobalExceptionHandler}
 * (design.md — REST endpoints): {@code {timestamp, status, error, message, path}}.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
