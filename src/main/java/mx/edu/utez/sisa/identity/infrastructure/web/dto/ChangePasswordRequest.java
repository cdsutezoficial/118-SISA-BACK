package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /auth/change-password} (design.md — REST
 * endpoints).
 */
public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
}
