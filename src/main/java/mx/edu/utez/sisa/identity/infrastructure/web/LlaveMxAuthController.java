package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.LlaveMxAuthenticationResult;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.LlaveMxProfileVerification;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.StartResult;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxPkceGenerator;
import mx.edu.utez.sisa.identity.infrastructure.config.LlaveMxProperties;
import mx.edu.utez.sisa.identity.infrastructure.security.LlaveMxVerificationTicketService;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxAccountNotLinkedException;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxIntegrationException;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.LlaveMxAvailabilityResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.LlaveMxStartResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Llave Morelos OAuth 2.0 endpoints — the HTTP face of the Authorization Code
 * Grant with PKCE.
 *
 * <p>Two flavors share one provider flow:
 * <ul>
 * <li>{@code staff} (RF-AUTH-002) — sign a SISA account in with LlaveMX.</li>
 * <li>{@code candidate} (RF-AUTH-003) — verify an applicant's identity for the
 * ficha registration. No account is created; the answer is the identity only,
 * because a candidate has a folio, not a login (er-02-identidad.md).</li>
 * </ul>
 *
 * <p>The PKCE {@code code_verifier} and the anti-CSRF {@code state} travel in a
 * short-lived HttpOnly cookie instead of server-side session state: the secret
 * never reaches JavaScript, and no API instance is pinned to a flow started on
 * another.
 *
 * <p><b>Why the callback redirects instead of answering JSON.</b> The provider
 * redirects a browser here, so a JSON body would render as raw text in the
 * address bar. The callback therefore finishes the exchange server-side and
 * 302s the browser back to the front with the tokens in the URL
 * <em>fragment</em>: fragments are never sent in an HTTP request, so unlike
 * query parameters they cannot land in proxy or access logs. The front reads
 * {@code location.hash}, clears it with {@code history.replaceState}, and
 * persists the tokens the same way it already does for local login.
 */
@RestController
public class LlaveMxAuthController {

	private static final Logger log = LoggerFactory.getLogger(LlaveMxAuthController.class);

	/** Which flow a started round belongs to; a callback may not switch it. */
	private static final String FLAVOR_STAFF = "staff";
	private static final String FLAVOR_CANDIDATE = "candidate";

	private static final String SEPARATOR = "|";
	/** {@link #SEPARATOR} quoted: a bare {@code |} is an empty regex alternation. */
	private static final String SEPARATOR_REGEX = "\\|";

	private final AuthenticateWithLlaveMxUseCase authenticateWithLlaveMxUseCase;
	private final LlaveMxOAuthPort llaveMxOAuthPort;
	private final LlaveMxProperties properties;
	private final LlaveMxVerificationTicketService verificationTicketService;

	public LlaveMxAuthController(AuthenticateWithLlaveMxUseCase authenticateWithLlaveMxUseCase,
			LlaveMxOAuthPort llaveMxOAuthPort, LlaveMxProperties properties,
			LlaveMxVerificationTicketService verificationTicketService) {
		this.authenticateWithLlaveMxUseCase = authenticateWithLlaveMxUseCase;
		this.llaveMxOAuthPort = llaveMxOAuthPort;
		this.properties = properties;
		this.verificationTicketService = verificationTicketService;
	}

	/**
	 * Lets the front decide whether to render the LlaveMX button at all, so an
	 * unconfigured environment never sends an applicant into a round that
	 * cannot finish.
	 */
	@GetMapping("/auth/llave-mx/status")
	public ResponseEntity<LlaveMxAvailabilityResponse> status() {
		return ResponseEntity.ok(new LlaveMxAvailabilityResponse(llaveMxOAuthPort.isConfigured()));
	}

	/**
	 * Starts a round: mints state + verifier, stores them in an HttpOnly cookie
	 * and hands back the consent URL. Answers 503 when the integration is
	 * unconfigured.
	 */
	@PostMapping("/auth/llave-mx/start")
	public ResponseEntity<LlaveMxStartResponse> start(@RequestParam(defaultValue = FLAVOR_STAFF) String flavor,
			HttpServletResponse response) {
		if (!llaveMxOAuthPort.isConfigured()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(new LlaveMxStartResponse(false, null, null));
		}
		String resolvedFlavor = FLAVOR_CANDIDATE.equals(flavor) ? FLAVOR_CANDIDATE : FLAVOR_STAFF;
		StartResult result = authenticateWithLlaveMxUseCase.start();
		writeFlowCookie(response, result.state(), result.codeVerifier(), resolvedFlavor, null);
		return ResponseEntity.ok(new LlaveMxStartResponse(true, result.authorizationUrl(), result.state()));
	}

	/**
	 * Starts a <em>linking</em> round (RF-AUTH-002 "vinculacion posterior"): the
	 * admin is already signed in, so this one is not public — the caller's JWT
	 * is what authorizes it, and {@code userId} says which account gains the
	 * binding.
	 *
	 * <p>Both values are the server's to remember, not the browser's. The tab
	 * is about to leave for the provider and will come back with only a
	 * {@code state}, so the target account rides in the same HttpOnly
	 * state-named cookie as the PKCE verifier: the callback can only read it by
	 * presenting the matching state, and a forged callback has no such cookie.
	 */
	@PostMapping("/auth/llave-mx/link")
	public ResponseEntity<LlaveMxStartResponse> linkStart(@RequestParam UUID userId,
			HttpServletResponse response) {
		if (!llaveMxOAuthPort.isConfigured()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(new LlaveMxStartResponse(false, null, null));
		}
		StartResult result = authenticateWithLlaveMxUseCase.start();
		writeFlowCookie(response, result.state(), result.codeVerifier(), FLAVOR_STAFF, userId.toString());
		return ResponseEntity.ok(new LlaveMxStartResponse(true, result.authorizationUrl(), result.state()));
	}

	/**
	 * Provider callback. The provider redirects with a GET; the POST overload
	 * lets the front drive the same exchange if it captures the code itself.
	 *
	 * <p>Three paths, one handler. The hub's registration form makes
	 * {@code /oauth/llave/digital/callback} mandatory and rejects a client that
	 * registers anything else, so that path has to exist regardless of the
	 * {@code /auth/llave-mx/*} family the rest of the flow uses. The prefixed
	 * variant covers the deployment where the reverse proxy forwards the whole
	 * registered URL instead of stripping the {@code /SISAVA} segment, and the
	 * bare one where it does strip it. The two cannot both be hit in a single
	 * request, so whichever the proxy produces lands here and the other is inert.
	 */
	@GetMapping({ "/auth/llave-mx/callback", "/oauth/llave/digital/callback",
			"/SISAVA/oauth/llave/digital/callback" })
	public void callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
			@RequestParam(required = false) String error, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		finish(code, state, error, request, response);
	}

	@PostMapping({ "/auth/llave-mx/callback", "/oauth/llave/digital/callback",
			"/SISAVA/oauth/llave/digital/callback" })
	public void callbackPost(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
			@RequestParam(required = false) String error, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		finish(code, state, error, request, response);
	}

	private void finish(String code, String state, String error, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		// Read and clear the cookie first: whatever happens below, the PKCE
		// material is single-use and must not linger.
		String stored = readFlowCookie(request, state);
		clearFlowCookie(response, state);

		// A candidate who declined consent never left the registration wizard, so
		// their failure has to land back in the wizard. Sending them to /login —
		// where no applicant has any business being — reads as "your account is
		// broken". The flavor comes from the cookie, so a missing cookie falls
		// back to staff, which is the only flow that was ever visible at /login.
		boolean candidate = FLAVOR_CANDIDATE.equals(flavorOf(stored));
		// Same reasoning one level up: a failed link must land back on the
		// profile that started it, not on the login screen, where the admin
		// already is and where the failure would read as "your session died".
		UUID linkUserId = linkUserIdOf(stored);
		String landing = linkUserId != null ? "/usuarios/detalle?id=" + linkUserId
				: candidate ? "/portal/registro" : "/login";

		if (error != null && !error.isBlank()) {
			// The applicant declined consent. `error` is provider-controlled and
			// may name internal codes, so it is logged rather than shown.
			log.info("LlaveMX flow ended with provider error '{}'", error);
			response.sendRedirect(fragmentRedirect(landing, "error",
					"No se autorizo el acceso a tu identidad."));
			return;
		}

		String verifier = verifierOf(stored);
		try {
			if (linkUserId != null) {
				redirectLink(code, state, verifier, linkUserId, response);
			} else if (candidate) {
				redirectCandidate(code, state, verifier, response);
			} else {
				redirectStaff(code, state, verifier, response);
			}
		} catch (RuntimeException e) {
			log.warn("LlaveMX flow failed on {}: {}", landing, e.getMessage());
			// Only our own exceptions carry copy written for a person. Anything
			// else is a bug or a leaked library message, and putting that in a URL
			// the browser will display (and the user can screenshot) is how
			// internals escape. They still get a redirect — a bare error page here
			// would strand them on the API origin with no way back.
			response.sendRedirect(fragmentRedirect(landing, "error", messageFor(e)));
		}
	}

	/**
	 * Applicant-facing text for a failed round, or a neutral fallback for failures
	 * nobody wrote copy for.
	 */
	private String messageFor(RuntimeException e) {
		if (e instanceof LlaveMxAccountNotLinkedException || e instanceof LlaveMxIntegrationException) {
			return e.getMessage();
		}
		return "No pudimos completar la verificacion con Llave Morelos. Intenta de nuevo.";
	}

	private void redirectStaff(String code, String state, String verifier, HttpServletResponse response)
			throws IOException {
		LlaveMxAuthenticationResult result = authenticateWithLlaveMxUseCase.complete(code, state, verifier);
		// The same three values the local login answers with, so the front can
		// hand them to its one `persistSession` path unchanged. They ride in the
		// fragment, never the query string: a fragment is not part of the HTTP
		// request, so it stays out of proxy logs and out of any `Referer` sent on
		// the next navigation. The front scrubs it with `history.replaceState`.
		response.sendRedirect(properties.frontendBaseUrl() + "/login#llave=" + encode(result.accessToken())
				+ "&refresh=" + encode(result.refreshToken()) + "&mustChangePassword="
				+ result.mustChangePassword());
	}

	private void redirectLink(String code, String state, String verifier, UUID userId, HttpServletResponse response)
			throws IOException {
		authenticateWithLlaveMxUseCase.link(code, state, verifier, userId);
		// No session: linking edited an account, it did not sign anyone in. The
		// marker is a single boolean carried in the fragment so the profile can
		// toast the outcome; it grants nothing and forges to nothing, because
		// the badge it refreshes is re-read from GET /users/{id} anyway.
		response.sendRedirect(properties.frontendBaseUrl() + "/usuarios/detalle?id=" + userId
				+ "#llaveLinked=ok");
	}

	private void redirectCandidate(String code, String state, String verifier, HttpServletResponse response)
			throws IOException {
		LlaveMxProfileVerification verification = authenticateWithLlaveMxUseCase.verifyIdentity(code, state,
				verifier);
		// The identity itself goes into a signed, short-lived ticket in an HttpOnly
		// cookie. It is deliberately NOT put in the redirect query string: a URL is
		// the one part of a browser round trip that leaks into history, logs and
		// Referer headers, and an applicant could hand-edit one to claim a CURP
		// they never proved.
		String ticket = verificationTicketService.issue(verification.subject(), verification.curp(),
				verification.fullName(), verification.institutionalEmail());

		Cookie ticketCookie = new Cookie(LlaveMxVerificationTicketService.COOKIE_NAME, ticket);
		ticketCookie.setHttpOnly(true);
		// Path is the registration endpoint, not "/": the ticket is only ever
		// needed by that POST, and narrowing it keeps the value from riding along
		// on every other request the browser makes.
		ticketCookie.setPath(LlaveMxVerificationTicketService.COOKIE_PATH);
		ticketCookie.setMaxAge((int) properties.verificationTicketTtl().toSeconds());
		ticketCookie.setSecure(isHttps());
		response.addCookie(ticketCookie);

		response.sendRedirect(properties.frontendBaseUrl() + "/portal/registro?llave=verified");
	}
	/**
	 * Stores {@code verifier|flavor[|linkUserId]} in a short-lived HttpOnly
	 * cookie. The cookie is named after the state, so the callback can only find
	 * the round it actually started — a stale cookie from another tab is never
	 * read.
	 *
	 * <p>The third field is present only for a linking round. It is written by
	 * the server from its own authenticated caller, so the browser never
	 * chooses which account gets bound; it only carries the value back by
	 * presenting the matching state.
	 */
	private void writeFlowCookie(HttpServletResponse response, String state, String codeVerifier, String flavor,
			String linkUserId) {
		String value = codeVerifier + SEPARATOR + flavor + SEPARATOR + (linkUserId == null ? "" : linkUserId);
		Cookie cookie = new Cookie(cookieName(state), value);
		cookie.setHttpOnly(true);
		// Lax rather than Strict: the callback is a top-level GET navigation
		// arriving from the provider's origin, and Strict would withhold the
		// cookie exactly when it is needed.
		cookie.setPath("/");
		cookie.setMaxAge((int) properties.flowTtl().toSeconds());
		cookie.setSecure(isHttps());
		response.addCookie(cookie);
	}

	private String readFlowCookie(HttpServletRequest request, String state) {
		if (!isPresent(state)) {
			return null;
		}
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		String name = cookieName(state);
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/** Single-use: expired immediately so a leaked code cannot be replayed. */
	private void clearFlowCookie(HttpServletResponse response, String state) {
		if (!isPresent(state)) {
			return;
		}
		Cookie cookie = new Cookie(cookieName(state), "");
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		cookie.setMaxAge(0);
		cookie.setSecure(isHttps());
		response.addCookie(cookie);
	}

	private String cookieName(String state) {
		return LlaveMxPkceGenerator.cookieName(state);
	}

	/**
	 * Splits the cookie value. The join uses a literal {@code |}, which is also
	 * an empty alternation in regex — hence a quoted pattern — and
	 * {@code limit -1} keeps a trailing empty link field instead of dropping it,
	 * which is exactly what a non-linking round produces.
	 */
	private static String[] fieldsOf(String stored) {
		if (stored == null) {
			return new String[0];
		}
		return stored.split(SEPARATOR_REGEX, -1);
	}

	private String verifierOf(String stored) {
		String[] fields = fieldsOf(stored);
		return fields.length == 0 ? null : fields[0];
	}

	private String flavorOf(String stored) {
		String[] fields = fieldsOf(stored);
		return fields.length > 1 && !fields[1].isBlank() ? fields[1] : FLAVOR_STAFF;
	}

	/** The account a linking round binds, or {@code null} for the login flows. */
	private UUID linkUserIdOf(String stored) {
		String[] fields = fieldsOf(stored);
		if (fields.length < 3 || fields[2].isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(fields[2]);
		} catch (IllegalArgumentException e) {
			// Corrupt or hand-edited cookie. Falling back to null degrades to the
			// plain staff flow rather than redirecting somewhere unparseable.
			return null;
		}
	}

	/**
	 * Single-key fragment redirect, used for failures. The success path builds its
	 * own URL because it carries two values.
	 *
	 * <p>Values ride in the fragment, never the query string: a fragment is not
	 * part of the HTTP request, so it stays out of proxy logs and out of any
	 * {@code Referer} sent on the next navigation.
	 */
	private String fragmentRedirect(String path, String key, String value) {
		return properties.frontendBaseUrl() + path + "#" + key + "=" + encode(value);
	}

	private static String encode(String value) {
		return isPresent(value) ? URLEncoder.encode(value, StandardCharsets.UTF_8) : "";
	}

	/** Marks the flow cookie Secure whenever the public origin is HTTPS. */
	private boolean isHttps() {
		String base = properties.frontendBaseUrl();
		return base != null && base.startsWith("https://");
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}
