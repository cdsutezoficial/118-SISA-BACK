package mx.edu.utez.sisa.identity.infrastructure.web.dto;

/**
 * Body of {@code GET /auth/me}: the caller's own profile — full name from its
 * linked {@code Person}, username, and the best available email. Deliberately
 * lean: roles/status already travel in the access token and the capability
 * envelope, and the userId is the token {@code sub} (no point echoing it).
 * Only what the shell chrome (Navbar/Sidebar) displays lives here.
 */
public record MeProfileResponse(String fullName, String username, String email) {
}