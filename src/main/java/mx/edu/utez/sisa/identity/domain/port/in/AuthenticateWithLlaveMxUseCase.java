package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort.LlaveMxPrefill;

import java.util.UUID;

/**
 * Two-step Login-as-official with Llave Morelos (RF-AUTH-002).
 *
 * <p>Split in two because the browser has to bounce through the provider's
 * consent screen between them: {@link #start} produces the URL and the
 * PKCE/state material, {@link #complete} finishes once the provider redirects
 * back with an authorization code.
 *
 * <p>The caller authenticates <em>itself</em> (the browser that started the
 * flow), not a SISA user — the official is not known until the provider answers.
 */
public interface AuthenticateWithLlaveMxUseCase {

	/** PKCE verifier plus anti-CSRF state for one redirect round. */
	record StartResult(String authorizationUrl, String state, String codeVerifier) {
	}

	/**
	 * A successful official login, carrying the same token pair the local login
	 * issues.
	 *
	 * <p>{@code mustChangePassword} mirrors {@code User.isMustChangePassword()}
	 * rather than being hardcoded to false. The official never chose a local
	 * password, so the common case is false — but a support agent can flag an
	 * account as owing a password change, and that gate has to survive the
	 * choice of login method. Reporting false here would let the same account
	 * walk past a forced change just by signing in with LlaveMX instead.
	 */
	record LlaveMxAuthenticationResult(UUID userId, String accessToken, String refreshToken,
			boolean mustChangePassword) {
	}

	/**
	 * Builds the consent URL and the PKCE material. The caller must persist
	 * {@code state} and {@code codeVerifier} and hand them back to
	 * {@link #complete} — losing them makes the flow unrecoverable by design
	 * (that is what PKCE is for).
	 */
	StartResult start();

	/**
	 * Exchanges the authorization code for tokens, resolves the official's
	 * account by OAuth subject and issues the SISA token pair.
	 *
	 * @param expectedState the state this flow was started with; a mismatch is
	 *                      rejected as CSRF and the flow is discarded
	 * @throws mx.edu.utez.sisa.identity.shared.exception.LlaveMxAccountNotLinkedException
	 *         when the official has no SISA account, is {@code LOCKED}, or has
	 *         no active role
	 */
	LlaveMxAuthenticationResult complete(String authorizationCode, String expectedState, String codeVerifier);

	/**
	 * Verifies an official's identity without creating a session — the
	 * candidate path (RF-AUTH-003), where no SISA account exists or is needed.
	 */
	LlaveMxProfileVerification verifyIdentity(String authorizationCode, String expectedState, String codeVerifier);

	/**
	 * Binds the OAuth subject proven by this round to an already-existing SISA
	 * account — the RF-AUTH-002 "vinculacion posterior desde el perfil" step.
	 *
	 * <p>Deliberately no session: linking is an edit to an account, not a way
	 * into it, so the answer is only "this account is now linked". A separate
	 * login still has to satisfy its own gates (status, active roles).
	 *
	 * <p>The subject is looked up across <em>every</em> account before writing,
	 * because {@code llave_mx_subject} is the login key: silently retargeting it
	 * would move a person's login from one account to another.
	 *
	 * @param userId the account being linked, resolved server-side from the
	 *               flow cookie rather than from anything the browser sent on
	 *               the callback
	 * @throws mx.edu.utez.sisa.identity.shared.exception.LlaveMxAccountNotLinkedException
	 *         when the subject already belongs to a different account
	 */
	LlaveMxLinkResult link(String authorizationCode, String expectedState, String codeVerifier, UUID userId);

	/** Linking changed one account, and said which. Carries no tokens. */
	record LlaveMxLinkResult(UUID userId) {
	}

	record LlaveMxProfileVerification(String subject, String institutionalEmail, String fullName, String curp,
			java.util.List<String> roles, LlaveMxPrefill prefill) {
	}
}
