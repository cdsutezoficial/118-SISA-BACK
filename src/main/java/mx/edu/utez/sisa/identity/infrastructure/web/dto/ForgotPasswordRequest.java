package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /auth/forgot-password} (01-identidad.md —
 * RequestPasswordResetUseCase).
 */
public record ForgotPasswordRequest(@NotBlank String username) {
}