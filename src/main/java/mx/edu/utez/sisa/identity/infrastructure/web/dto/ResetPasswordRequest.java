package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /auth/reset-password} (01-identidad.md —
 * ResetPasswordUseCase).
 */
public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
}