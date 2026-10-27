package mx.edu.utez.sisa.identity.infrastructure.web.dto;

/**
 * Response of {@code POST /auth/llave-mx/start}. The state is echoed so the
 * front can correlate the round, but the PKCE verifier is never included — it
 * only ever lives in the HttpOnly cookie the same response sets.
 *
 * @param configured false when the environment has no LlaveMX credentials; the
 *                   front then hides the button
 * @param authorizationUrl where to send the browser, or null when unconfigured
 * @param state anti-CSRF nonce for this round
 */
public record LlaveMxStartResponse(boolean configured, String authorizationUrl, String state) {
}
