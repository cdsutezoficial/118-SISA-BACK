package mx.edu.utez.sisa.identity.infrastructure.web.dto;

/**
 * Response body for {@code POST /auth/refresh} (design.md — REST endpoints):
 * {@code {accessToken, tokenType:"Bearer", expiresIn}}.
 */
public record RefreshResponse(String accessToken, String tokenType, long expiresIn) {
}
