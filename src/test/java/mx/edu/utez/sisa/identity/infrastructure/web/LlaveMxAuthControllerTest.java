package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.servlet.http.Cookie;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.LlaveMxAuthenticationResult;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.LlaveMxLinkResult;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.LlaveMxProfileVerification;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.StartResult;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort.LlaveMxPrefill;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxPkceGenerator;
import mx.edu.utez.sisa.identity.infrastructure.config.LlaveMxProperties;
import mx.edu.utez.sisa.identity.infrastructure.security.LlaveMxVerificationTicketService;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.LlaveMxStartResponse;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxAccountNotLinkedException;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security-relevant half of the Llave Morelos flow, which is the half only
 * a web test reaches: cookie flags, the state round-trip, and what the callback
 * will do with a query string it did not issue.
 *
 * <p>Built with a hand-wired controller rather than {@code @WebMvcTest} on
 * purpose: the decisions under test (TTL, frontend origin, the Secure flag) come
 * from {@link LlaveMxProperties}, and a context-managed properties bean would
 * silently fall back to its defaults instead of the HTTPS origin a production
 * deployment uses.
 */
@ExtendWith(MockitoExtension.class)
class LlaveMxAuthControllerTest {

	private static final String FRONT = "https://sisa.utez.edu.mx";
	private static final String CODE = "code-123";
	private static final String STATE = "s1";
	private static final String VERIFIER = "verifier-abc";

	/** El subconjunto que RF-AUTH-003 prellena en el formulario del candidato. */
	private static final LlaveMxPrefill PREFILL = new LlaveMxPrefill("Ada", "Lovelace", "Byron", "10/12/1815",
			"Femenino", "Ciudad de Mexico", "Cuauhtemoc", "ada@utez.edu.mx", "5512345678");

	@Mock
	private AuthenticateWithLlaveMxUseCase authenticateWithLlaveMxUseCase;
	@Mock
	private LlaveMxOAuthPort llaveMxOAuthPort;
	@Mock
	private LlaveMxVerificationTicketService verificationTicketService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(controller(FRONT)).build();
	}

	@Test
	void status_reportsConfiguredSoTheFrontCanHideTheButton() throws Exception {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);

		mockMvc.perform(get("/auth/llave-mx/status")).andExpect(status().isOk());
		assertThat(body(get("/auth/llave-mx/status"))).contains("\"configured\":true");
	}

	@Test
	void status_neverLeaksCredentials() throws Exception {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(false);

		String body = body(get("/auth/llave-mx/status"));

		assertThat(body).contains("\"configured\":false")
				.doesNotContain("client-secret")
				.doesNotContain("/api/v1/llave");
	}

	@Test
	void start_refusesWhenTheIntegrationIsNotConfigured() throws Exception {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(false);

		mockMvc.perform(post("/auth/llave-mx/start")).andExpect(status().isServiceUnavailable());

		verify(authenticateWithLlaveMxUseCase, never()).start();
	}

	@Test
	void start_returnsTheConsentUrlButNeverTheVerifier() throws Exception {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(authenticateWithLlaveMxUseCase.start())
				.thenReturn(new StartResult("https://simple.morelos.gob.mx/oauth/authorize?state=s1", STATE,
						"super-secret-verifier"));

		String body = body(post("/auth/llave-mx/start"));

		// The verifier is the whole secret PKCE protects: cookie only, never a
		// response field the browser can read.
		assertThat(body).contains("\"state\":\"s1\"").doesNotContain("super-secret-verifier");
	}

	@Test
	void start_writesAnHttpOnlySecureCookieNamedAfterTheState() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(authenticateWithLlaveMxUseCase.start())
				.thenReturn(new StartResult("https://provider/authorize", STATE, VERIFIER));
		MockHttpServletResponse response = new MockHttpServletResponse();

		controller(FRONT).start("staff", response);

		Cookie cookie = response.getCookie(LlaveMxPkceGenerator.cookieName(STATE));
		assertThat(cookie).isNotNull();
		assertThat(cookie.isHttpOnly()).isTrue();
		// An HTTPS frontend in production must not send the verifier in the clear.
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofMinutes(5).toSeconds());
		// The third field is the (empty) link target: a login round binds no
		// account, and the callback reads the absence from that slot.
		assertThat(cookie.getValue()).isEqualTo(VERIFIER + "|staff|");
	}

	@Test
	void start_leavesTheCookieUnsecureOnlyForALocalHttpFrontend() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(authenticateWithLlaveMxUseCase.start())
				.thenReturn(new StartResult("https://provider/authorize", STATE, VERIFIER));
		MockHttpServletResponse response = new MockHttpServletResponse();

		controller("http://localhost:5173").start("staff", response);

		assertThat(response.getCookie(LlaveMxPkceGenerator.cookieName(STATE)).getSecure()).isFalse();
	}

	@Test
	void callback_readsTheVerifierFromTheStateNamedCookie() throws Exception {
		when(authenticateWithLlaveMxUseCase.complete(CODE, STATE, VERIFIER))
				.thenReturn(new LlaveMxAuthenticationResult(UUID.randomUUID(), "access-token-value", "refresh", false));

		mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE).param("state", STATE)
				.cookie(flowCookie(STATE, VERIFIER + "|staff"))).andExpect(status().is3xxRedirection());

		verify(authenticateWithLlaveMxUseCase).complete(CODE, STATE, VERIFIER);
	}

	/**
	 * The hub's registration form makes {@code /oauth/llave/digital/callback}
	 * the only redirect URL it accepts, and {@code /auth/llave-mx/callback}
	 * would 404 there. Both spellings of the registered URL — with and without
	 * the proxy's {@code /SISAVA} prefix — have to reach the same handler.
	 */
	@Test
	void callback_isAlsoReachableAtThePathTheHubMandates() throws Exception {
		when(authenticateWithLlaveMxUseCase.complete(CODE, STATE, VERIFIER))
				.thenReturn(new LlaveMxAuthenticationResult(UUID.randomUUID(), "access-token-value", "refresh", false));

		for (String path : List.of("/oauth/llave/digital/callback", "/SISAVA/oauth/llave/digital/callback")) {
			MvcResult result = mockMvc.perform(get(path).param("code", CODE).param("state", STATE)
					.cookie(flowCookie(STATE, VERIFIER + "|staff"))).andExpect(status().is3xxRedirection()).andReturn();

			assertThat(result.getResponse().getRedirectedUrl()).startsWith(FRONT + "/login#llave=");
		}
	}

	/**
	 * Two tabs run two rounds. A cookie from the other round must not authorise
	 * this one's code, which is exactly what keying the cookie by state buys.
	 */
	@Test
	void callback_ignoresACookieBelongingToAnotherRound() throws Exception {

		mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE).param("state", STATE)
				.cookie(flowCookie("another-round", VERIFIER + "|staff"))).andExpect(status().is3xxRedirection());

		verify(authenticateWithLlaveMxUseCase).complete(eq(CODE), eq(STATE), isNull());
	}

	@Test
	void callback_clearsTheFlowCookieWhateverHappens() throws Exception {
		when(authenticateWithLlaveMxUseCase.complete(anyString(), anyString(), anyString()))
				.thenThrow(new LlaveMxIntegrationException("LlaveMX no esta disponible."));

		MvcResult result = mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE).param("state", STATE)
				.cookie(flowCookie(STATE, VERIFIER + "|staff"))).andExpect(status().is3xxRedirection())
				.andReturn();

		// Single-use: the PKCE material must not survive the round it served.
		assertThat(setCookies(result)).contains("sisa.llave.flow.s1=")
				.contains("Max-Age=0")
				.doesNotContain("verifier-abc");
	}

	@Test
	void callback_staffHandsTheAccessTokenToTheFrontInTheFragmentNotTheQuery() throws Exception {
		when(authenticateWithLlaveMxUseCase.complete(CODE, STATE, VERIFIER))
				.thenReturn(new LlaveMxAuthenticationResult(UUID.randomUUID(), "access-token-value", "refresh", false));

		String location = location(mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE)
				.param("state", STATE).cookie(flowCookie(STATE, VERIFIER + "|staff"))));

		assertThat(location).startsWith(FRONT + "/login#llave=access-token-value")
				// A token in the query string reaches proxy logs and Referer headers.
				.doesNotContain("?llave=");
	}

	@Test
	void callback_providerErrorIsLoggedNotEchoed() throws Exception {
		String location = location(mockMvc.perform(get("/auth/llave-mx/callback").param("state", STATE)
				.param("error", "access_denied:internal_detail_42").cookie(flowCookie(STATE, VERIFIER + "|staff"))));

		assertThat(location).startsWith(FRONT + "/login#error=").doesNotContain("internal_detail_42");
		verify(authenticateWithLlaveMxUseCase, never()).complete(any(), any(), any());
	}

	@Test
	void callback_candidateFlowIssuesAVerificationTicketInsteadOfASession() throws Exception {
		when(authenticateWithLlaveMxUseCase.verifyIdentity(CODE, STATE, VERIFIER))
				.thenReturn(new LlaveMxProfileVerification("subject-1", "ada@utez.edu.mx", "Ada Lovelace",
						"CURP123456HDFRPN01", List.of("DOCENTE"), PREFILL));
		when(verificationTicketService.issue("subject-1", "CURP123456HDFRPN01", "Ada Lovelace", "ada@utez.edu.mx",
				PREFILL)).thenReturn("signed-ticket");
		MockHttpServletResponse response = new MockHttpServletResponse();

		controller(FRONT).callback(CODE, STATE, null, request(), response);

		// The identity must not ride in the URL: history, proxy logs and Referer
		// headers all capture it, and a hand-edited one would claim a CURP.
		assertThat(response.getRedirectedUrl()).startsWith(FRONT + "/portal/registro")
				.doesNotContain("CURP123456HDFRPN01")
				.doesNotContain("subject-1");
		Cookie ticket = response.getCookie(LlaveMxVerificationTicketService.COOKIE_NAME);
		assertThat(ticket).isNotNull();
		assertThat(ticket.isHttpOnly()).isTrue();
		assertThat(ticket.getValue()).isEqualTo("signed-ticket");
		assertThat(ticket.getPath()).isEqualTo(LlaveMxVerificationTicketService.COOKIE_PATH);
		// A candidate has a folio, not a login: no SISA session may be issued.
		verify(authenticateWithLlaveMxUseCase, never()).complete(any(), any(), any());
	}

	@Test
	void callback_candidateFlowCannotBeTurnedIntoASessionByReplayingTheStaffFlavor() throws Exception {
		when(authenticateWithLlaveMxUseCase.verifyIdentity(CODE, STATE, VERIFIER))
				.thenReturn(new LlaveMxProfileVerification("subject-1", "a@b.mx", "Nombre", "CURP", List.of(), null));
		when(verificationTicketService.issue(anyString(), any(), any(), any(), any())).thenReturn("signed-ticket");
		MockHttpServletResponse response = new MockHttpServletResponse();

		controller(FRONT).callback(CODE, STATE, null, request(), response);

		verify(authenticateWithLlaveMxUseCase).verifyIdentity(CODE, STATE, VERIFIER);
		verify(authenticateWithLlaveMxUseCase, never()).complete(any(), any(), any());
	}

	@Test
	void callback_unlinkedOfficialGetsAnExplanationNotAToken() throws Exception {
		when(authenticateWithLlaveMxUseCase.complete(anyString(), anyString(), anyString()))
				.thenThrow(new LlaveMxAccountNotLinkedException("Tu cuenta no esta vinculada a LlaveMX."));

		String location = location(mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE)
				.param("state", STATE).cookie(flowCookie(STATE, VERIFIER + "|staff"))));

		assertThat(location).contains("vinculada").doesNotContain("access-token");
	}

	@Test
	void callback_withoutCookiesStillAnswersCleanlyInsteadOfErroring() throws Exception {

		mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE).param("state", STATE))
				.andExpect(status().is3xxRedirection());

		verify(authenticateWithLlaveMxUseCase).complete(CODE, STATE, null);
	}

	@Test
	void callback_withoutStateIsAnsweredNotCrashed() throws Exception {
		mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE)).andExpect(status().is3xxRedirection());

		// The controller still forwards it; the use case is what rejects the
		// missing state, and duplicating that check here would mean two places
		// to keep in step for no extra safety.
		verify(authenticateWithLlaveMxUseCase).complete(CODE, null, null);
	}

	@Test
	void start_answers503WhenTheIntegrationIsOff() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(false);

		ResponseEntity<LlaveMxStartResponse> response = controller(FRONT).start("staff",
				new MockHttpServletResponse());

		// The front needs the body to know the button is unavailable; a bare 503
		// would leave it guessing.
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().configured()).isFalse();
		assertThat(response.getBody().authorizationUrl()).isNull();
	}

	// ─── Linking (RF-AUTH-002 "vinculacion posterior desde el perfil") ──────────

	@Test
	void linkStart_writesTheTargetAccountIntoTheStateNamedCookie() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(authenticateWithLlaveMxUseCase.start())
				.thenReturn(new StartResult("https://provider/authorize", STATE, VERIFIER));
		UUID target = UUID.randomUUID();
		MockHttpServletResponse response = new MockHttpServletResponse();

		controller(FRONT).linkStart(target, response);

		Cookie cookie = response.getCookie(LlaveMxPkceGenerator.cookieName(STATE));
		assertThat(cookie).isNotNull();
		// The account to bind is the server's to remember. The tab leaves for the
		// provider and returns with only a state, so if this were not written
		// here the target would have to ride through the browser instead.
		assertThat(cookie.getValue()).isEqualTo(VERIFIER + "|staff|" + target);
	}

	@Test
	void linkStart_refusesWhenTheIntegrationIsNotConfigured() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(false);

		ResponseEntity<LlaveMxStartResponse> response = controller(FRONT).linkStart(UUID.randomUUID(),
				new MockHttpServletResponse());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		verify(authenticateWithLlaveMxUseCase, never()).start();
	}

	@Test
	void callback_linkRoundBindsTheAccountAndLandsBackOnTheProfile() throws Exception {
		UUID target = UUID.randomUUID();
		when(authenticateWithLlaveMxUseCase.link(CODE, STATE, VERIFIER, target))
				.thenReturn(new LlaveMxLinkResult(target));

		String location = location(mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE)
				.param("state", STATE).cookie(flowCookie(STATE, VERIFIER + "|staff|" + target))));

		assertThat(location).isEqualTo(FRONT + "/usuarios/detalle?id=" + target + "#llaveLinked=ok");
		// Linking edited an account; it must not be able to hand back a session
		// the account's own gates (status, active roles) never approved.
		verify(authenticateWithLlaveMxUseCase, never()).complete(any(), any(), any());
	}

	@Test
	void callback_linkFailureLandsBackOnTheProfileNotTheLoginScreen() throws Exception {
		UUID target = UUID.randomUUID();
		when(authenticateWithLlaveMxUseCase.link(any(), any(), any(), any())).thenThrow(
				new LlaveMxAccountNotLinkedException("Ya existe una cuenta de SISA vinculada a esa identidad."));

		String location = location(mockMvc.perform(get("/auth/llave-mx/callback").param("code", CODE)
				.param("state", STATE).cookie(flowCookie(STATE, VERIFIER + "|staff|" + target))));

		// /login would read as "your session died" to an admin who never left
		// theirs, and would drop them away from the button they just pressed.
		assertThat(location).startsWith(FRONT + "/usuarios/detalle?id=" + target + "#error=");
	}

	private LlaveMxAuthController controller(String frontendBaseUrl) {
		return new LlaveMxAuthController(authenticateWithLlaveMxUseCase, llaveMxOAuthPort,
				properties(frontendBaseUrl), verificationTicketService);
	}

	private LlaveMxProperties properties(String frontendBaseUrl) {
		return new LlaveMxProperties("client-id", "client-secret",
				"https://simple.morelos.gob.mx/oauth/authorize",
				"https://hub.morelos.gob.mx/oauth/token",
				"https://hub.morelos.gob.mx/oauth/token/refresh",
				"https://hub.morelos.gob.mx/api/v1/llave/digital/entity",
				FRONT + "/api/auth/llave-mx/callback", "", "consent", frontendBaseUrl,
				Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofSeconds(10), "curp");
	}

	/** Request carrying the round's flow cookie, as the callback would see it. */
	private MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie[] { flowCookie(STATE, VERIFIER + "|candidate") });
		return request;
	}

	private String body(org.springframework.test.web.servlet.RequestBuilder builder) throws Exception {
		return mockMvc.perform(builder).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String location(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
		MvcResult result = actions.andReturn();
		assertThat(result.getResponse().getStatus()).isBetween(300, 399);
		return result.getResponse().getHeader(HttpHeaders.LOCATION);
	}

	private static String setCookies(MvcResult result) {
		return String.join("\n", result.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
	}

	private static Cookie flowCookie(String state, String value) {
		Cookie cookie = new Cookie(LlaveMxPkceGenerator.cookieName(state), value);
		cookie.setPath("/");
		return cookie;
	}
}
