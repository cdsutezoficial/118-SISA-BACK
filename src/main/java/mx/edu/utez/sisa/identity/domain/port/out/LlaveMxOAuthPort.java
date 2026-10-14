package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port for the Llave Morelos / Digital Morelos OAuth 2.0 provider
 * ("LlaveMX" in the older design docs — same provider, Morelos state
 * government edition of the identity platform).
 *
 * <p>Authorization Code Grant with PKCE. The provider splits the flow across
 * two hosts: consent happens on {@code simple.morelos.gob.mx} while the token
 * exchange and the identity lookup live on {@code hub.morelos.gob.mx}.
 *
 * <p>The client secret never leaves the backend — every method here is called
 * server-side. Nothing in this interface is reachable from a browser.
 */
public interface LlaveMxOAuthPort {

	/**
	 * Builds the consent URL the browser is redirected to. The caller persists
	 * {@code state} and the PKCE {@code codeVerifier} for the callback round.
	 *
	 * @param state    anti-CSRF nonce, echoed back by the provider
	 * @param challenge PKCE challenge, always {@code S256} (the provider
	 *                  requires the method and rejects a plain verifier)
	 */
	String buildAuthorizationUrl(String state, String challenge);

	/**
	 * Exchanges the single-use authorization code for tokens, proving possession
	 * of the PKCE verifier.
	 *
	 * @param authorizationCode code from the callback query string, single use
	 * @param codeVerifier     the verifier generated when the flow started
	 */
	LlaveMxTokenSet exchangeAuthorizationCode(String authorizationCode, String codeVerifier);

	/**
	 * Renews an access token with the refresh token (provider: {@code
	 * POST /oauth/token/refresh}).
	 */
	LlaveMxTokenSet refreshAccessToken(String refreshToken);

	/**
	 * Reads the authenticated official's profile with the access token
	 * (provider: {@code GET /api/v1/.../entity}). Carries the fields SISA
	 * needs for identification, role association and audit trail.
	 */
	LlaveMxProfile fetchProfile(String accessToken);

	/** True when client id/secret/redirect URI are all configured. */
	boolean isConfigured();

	record LlaveMxTokenSet(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
	}

	/**
	 * Provider identity payload. {@code subject} is the stable OAuth subject id
	 * and the only field trusted for account linking. The remaining fields are
	 * informational: the portal documents them as useful for identification,
	 * role association and audit, but their exact names and nullability are
	 * provider-defined, so everything beyond subject/email is optional.
	 *
	 * <p>{@code prefill} is the normalized subset the registration wizard shows
	 * read-only (RF-AUTH-003): it exists so the applicant never retypes what the
	 * provider already proved. Every component may be blank — the provider owns
	 * which of them it actually returns — and a blank one leaves that field
	 * editable instead of locking an empty box.
	 */
	record LlaveMxProfile(String subject, String institutionalEmail, String fullName, String curp,
			java.util.List<String> roles, LlaveMxPrefill prefill, java.util.Map<String, Object> raw) {
	}

	/**
	 * Identity fields carried from the provider's {@code person} payload into the
	 * ficha's step 1. Already in the shape SISA stores them, so the wizard does
	 * no provider-specific parsing: dates are {@code dd/MM/yyyy} and sex is the
	 * same {@code "Femenino"/"Masculino"} pair the DTO accepts.
	 *
	 * <p>Origin ({@code state}/{@code city}) is passed through as text rather
	 * than resolved to a catalog id here: the provider's encoding of a state is
	 * not part of the documented contract, and matching it against SISA's own
	 * catalog is the consumer's job, where an unmatched value can fall back to
	 * the manual control instead of silently selecting the wrong state.
	 */
	record LlaveMxPrefill(String nombres, String apellidoPaterno, String apellidoMaterno, String fechaNacimiento,
			String sexo, String estadoNacimiento, String municipioNacimiento, String email, String celular) {
	}
}
