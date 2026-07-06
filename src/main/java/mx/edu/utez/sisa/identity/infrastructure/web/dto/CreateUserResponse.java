package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /users} (design.md — REST endpoints):
 * {@code {userId, username, mustChangePassword:true}}.
 */
public record CreateUserResponse(UUID userId, String username, boolean mustChangePassword) {
}
