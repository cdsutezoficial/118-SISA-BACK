package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /users} (design.md — REST endpoints).
 */
public record CreateUserRequest(@NotNull UUID personId, @NotBlank String temporaryPassword) {
}
