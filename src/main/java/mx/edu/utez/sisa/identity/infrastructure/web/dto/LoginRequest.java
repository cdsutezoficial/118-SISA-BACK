package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /auth/login} (design.md — REST endpoints).
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
