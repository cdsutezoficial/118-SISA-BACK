package mx.edu.utez.sisa.identity.infrastructure.web.dto;

/**
 * Response body for {@code POST /auth/login} (design.md — REST endpoints):
 * {@code {accessToken, refreshToken, tokenType:"Bearer", expiresIn, mustChangePassword}}.
 */
public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
		boolean mustChangePassword) {
}
