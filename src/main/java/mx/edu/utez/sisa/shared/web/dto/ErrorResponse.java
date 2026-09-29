package mx.edu.utez.sisa.shared.web.dto;

import java.time.Instant;

/**
 * Uniform error body returned by every module's {@code GlobalExceptionHandler}
 * (design.md — REST endpoints): {@code {timestamp, status, error, message, path, code}}.
 *
 * <p>{@code code} is a stable, machine-readable discriminator that the frontend
 * branches on instead of pattern-matching {@code message}, which is written for
 * humans and changes wording freely. It is {@code null} for the handlers that
 * have not adopted one yet, so {@link #message} remains the fallback everywhere.
 *
 * <p>The five-argument constructor is kept so the modules that do not set a code
 * do not have to pass {@code null} at every call site.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
		String code) {

	public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
		this(timestamp, status, error, message, path, null);
	}
}
