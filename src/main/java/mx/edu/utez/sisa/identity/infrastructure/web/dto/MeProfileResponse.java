package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.UUID;

/**
 * Body of {@code GET /auth/me}: the caller's own profile — full name from its
 * linked {@code Person}, username, and the best available email. Deliberately
 * lean: roles/status already travel in the access token and the capability
 * envelope; only what the shell chrome (Navbar/Sidebar) displays lives here.
 */
public record MeProfileResponse(UUID userId, String fullName, String username, String email) {
}