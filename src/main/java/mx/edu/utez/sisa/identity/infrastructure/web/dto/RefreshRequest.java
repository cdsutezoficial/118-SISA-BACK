package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /auth/refresh} (design.md — REST endpoints).
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
