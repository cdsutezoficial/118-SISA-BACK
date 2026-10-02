package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /users/{userId}/reset-password} (plan
 * {@code 2026-10-02-admin-reset-password.md}):
 * {@code {userId, username, temporaryPassword}}.
 *
 * <p>{@code temporaryPassword} is the only place the plaintext is ever exposed.
 * It is generated per call and never persisted in this form, so this response
 * is the ADMIN's only chance to see it — the UI shows it once and discards it.
 */
public record AdminResetPasswordResponse(UUID userId, String username, String temporaryPassword) {
}