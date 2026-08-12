package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import mx.edu.utez.sisa.identity.domain.model.UserStatus;

import java.util.UUID;

/**
 * Response body for {@code PATCH /users/{id}/unlock} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.5):
 * {@code {userId, status, failedLoginAttempts}}.
 */
public record UnlockUserResponse(UUID userId, UserStatus status, int failedLoginAttempts) {
}
