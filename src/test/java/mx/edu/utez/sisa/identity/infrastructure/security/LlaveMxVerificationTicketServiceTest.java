package mx.edu.utez.sisa.identity.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort.LlaveMxPrefill;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxVerificationRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlaveMxVerificationTicketServiceTest {

	private static final String SECRET = "a-test-secret-that-is-long-enough-for-hmac-sha256-keys";
	private static final String SUBJECT = "morelos-subject-abc";

	/** The shape RF-AUTH-003 asks the wizard to pre-fill and lock. */
	private static final LlaveMxPrefill PREFILL = new LlaveMxPrefill("Ada", "Lovelace", "Byron", "10/12/1815",
			"Femenino", "Ciudad de Mexico", "Cuauhtemoc", "ada@utez.edu.mx", "5512345678");

	private LlaveMxVerificationTicketService service;

	@BeforeEach
	void setUp() {
		service = new LlaveMxVerificationTicketService(SECRET, Duration.ofMinutes(10));
	}

	@Test
	void issuedTicketVerifiesBackToTheSameIdentity() {
		String ticket = service.issue(SUBJECT, "CURP123456HDFRPN01", "Ada Lovelace", "ada@utez.edu.mx", PREFILL);

		var claims = service.verify(ticket);

		assertThat(claims.getSubject()).isEqualTo(SUBJECT);
		assertThat(claims.get("curp", String.class)).isEqualTo("CURP123456HDFRPN01");
		assertThat(claims.get("nombre", String.class)).isEqualTo("Ada Lovelace");
		assertThat(claims.get("correo", String.class)).isEqualTo("ada@utez.edu.mx");
		assertThat(service.prefillOf(claims)).isEqualTo(PREFILL);
		assertThat(service.curpOf(claims)).isEqualTo("CURP123456HDFRPN01");
	}

	@Test
	void blankProfileFieldsAreOmittedRatherThanStoredEmpty() {
		String ticket = service.issue(SUBJECT, null, "  ", "", new LlaveMxPrefill("", "", "", "", "", "", "", "",
				""));

		var claims = service.verify(ticket);

		assertThat(claims.get("curp")).isNull();
		assertThat(claims.get("nombre")).isNull();
		assertThat(claims.get("correo")).isNull();
		// A provider that withholds everything is not an error: the wizard reads
		// null back and leaves the whole step editable.
		assertThat(service.prefillOf(claims)).isNull();
	}

	@Test
	void tamperedPayloadIsRejected() {
		String ticket = service.issue(SUBJECT, "CURP123456HDFRPN01", "Ada Lovelace", null, PREFILL);
		// Flip a character in the payload segment; the signature no longer covers
		// what the token says, which is exactly the forgery the ticket prevents.
		String tampered = ticket.substring(0, 20) + (ticket.charAt(20) == 'A' ? 'B' : 'A')
				+ ticket.substring(21);

		assertThatThrownBy(() -> service.verify(tampered)).isInstanceOf(LlaveMxVerificationRequiredException.class);
	}

	@Test
	void ticketSignedWithAnotherSecretIsRejected() {
		LlaveMxVerificationTicketService attacker = new LlaveMxVerificationTicketService(
				"a-completely-different-secret-also-long-enough-here", Duration.ofMinutes(10));

		String forged = attacker.issue("attacker-subject", "CURP999999HDFRPN99", "Mallory", null, PREFILL);

		assertThatThrownBy(() -> service.verify(forged)).isInstanceOf(LlaveMxVerificationRequiredException.class);
	}

	@Test
	void expiredTicketIsRejected() {
		LlaveMxVerificationTicketService expiredIssuer = new LlaveMxVerificationTicketService(SECRET,
				Duration.ofMillis(1));
		String ticket = expiredIssuer.issue(SUBJECT, null, null, null, null);
		sleepPastExpiry();

		assertThatThrownBy(() -> service.verify(ticket)).isInstanceOf(LlaveMxVerificationRequiredException.class)
				.hasMessageContaining("expiro");
	}

	@Test
	void missingTicketIsRejectedWithActionableCopy() {
		assertThatThrownBy(() -> service.verify(null)).isInstanceOf(LlaveMxVerificationRequiredException.class)
				.hasMessageContaining("verificar tu identidad");
		assertThatThrownBy(() -> service.verify("  ")).isInstanceOf(LlaveMxVerificationRequiredException.class);
	}

	/**
	 * An access token must never be accepted as a verification ticket: they
	 * share the secret, so the {@code typ} claim is the only thing separating
	 * "proved who you are to register" from "you have a session".
	 */
	@Test
	void accessTokenIsNotAValidVerificationTicket() {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
		Instant now = Instant.now();
		String accessToken = Jwts.builder()
				.subject(SUBJECT)
				.claim("roles", java.util.Set.of("ADMIN"))
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(1800)))
				.signWith(key)
				.compact();

		assertThatThrownBy(() -> service.verify(accessToken)).isInstanceOf(LlaveMxVerificationRequiredException.class);
	}

	@Test
	void cookieIsScopedToTheEndpointsThatReadTheTicket() {
		// "/" rather than a single endpoint: the ticket answers both
		// POST /candidates and GET /auth/llave-mx/prefill, and scoping it to
		// either would make the other silently see no ticket.
		assertThat(LlaveMxVerificationTicketService.COOKIE_PATH).isEqualTo("/");
		assertThat(LlaveMxVerificationTicketService.COOKIE_NAME).doesNotContain("=").doesNotContain(" ");
	}

	private static void sleepPastExpiry() {
		try {
			Thread.sleep(1100);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
