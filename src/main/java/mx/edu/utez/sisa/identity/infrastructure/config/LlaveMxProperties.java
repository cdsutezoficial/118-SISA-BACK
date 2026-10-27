package mx.edu.utez.sisa.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Llave Morelos / Digital Morelos OAuth 2.0 settings (Authorization Code +
 * PKCE). Every value comes from the environment so no credential is ever
 * committed; blank means "integration off".
 *
 * <p>The provider documentation is internally inconsistent about the identity
 * endpoint: the endpoints table says
 * {@code /api/v1/llave/morelos/entity} while the usage example says
 * {@code /api/v1/llave/digital/entity}. Rather than guess, {@code entityUrl} has
 * <em>no default</em> — it is required, so the exact path is decided by
 * configuration once the registered app confirms which one it is.
 */
@ConfigurationProperties(prefix = "sisa.llave-mx")
public record LlaveMxProperties(
		/** OAuth client id issued by hub.morelos.gob.mx. */
		@DefaultValue("") String clientId,
		/** OAuth client secret. Server-side only — never returned by any endpoint. */
		@DefaultValue("") String clientSecret,
		/** Consent endpoint (simple.morelos.gob.mx). */
		@DefaultValue("https://simple.morelos.gob.mx/oauth/authorize") String authorizeUrl,
		/** Token exchange endpoint (hub.morelos.gob.mx). */
		@DefaultValue("https://hub.morelos.gob.mx/oauth/token") String tokenUrl,
		/** Access-token renewal endpoint (hub.morelos.gob.mx). */
		@DefaultValue("https://hub.morelos.gob.mx/oauth/token/refresh") String refreshUrl,
		/** Official identity endpoint. No default on purpose — see class javadoc. */
		@DefaultValue("") String entityUrl,
		/**
		 * Redirect (callback) URI exactly as registered at the hub. The provider
		 * rejects the flow on any mismatch, so it is configuration, never
		 * derived from the incoming request.
		 */
		@DefaultValue("") String redirectUri,
		/** Space-delimited scopes requested at consent time. */
		@DefaultValue("read:profile receive:notifications accept:tos accept:privacy") String scope,
		/** Forces the consent screen every time, per the provider's contract. */
		@DefaultValue("consent") String prompt,
		/**
		 * Public origin of the SPA. The provider callback is answered in the
		 * browser, so the controller must know where to send it back — and it
		 * doubles as the HTTPS test for marking the flow cookie {@code Secure}.
		 */
		@DefaultValue("") String frontendBaseUrl,
		/** How long the PKCE state/verifier cookie survives the redirect round. */
		@DefaultValue("PT5M") java.time.Duration flowTtl,
		/**
		 * Lifetime of the candidate verification ticket. Must comfortably exceed
		 * the time it takes to fill in the registration form, and nothing more.
		 */
		@DefaultValue("PT10M") java.time.Duration verificationTicketTtl,
	/** Upstream call timeout; the provider must not hold a login open. */
	@DefaultValue("PT10S") java.time.Duration timeout,
	/**
	 * Field of the {@code data.person} object used as the stable subject for
	 * account linking. The identity endpoint exposes no OAuth {@code sub}, and
	 * which of its identifiers the hub considers canonical is not documented, so
	 * the choice is configuration rather than a guess baked into the adapter.
	 */
	@DefaultValue("curp") String subjectField) {

	public boolean isConfigured() {
		return !isBlank(clientId) && !isBlank(clientSecret) && !isBlank(redirectUri) && !isBlank(entityUrl);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
