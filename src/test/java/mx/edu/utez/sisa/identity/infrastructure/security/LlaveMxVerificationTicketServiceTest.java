package mx.edu.utez.sisa.identity.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

	private LlaveMxVerificationTicketService service;

	@BeforeEach
	void setUp() {
		service = new LlaveMxVerificationTicketService(SECRET, Duration.ofMinutes(10));
	}

	@Test
	void issuedTicketVerifiesBackToTheSameIdentity() {
		String ticket = service.issue(SUBJECT, "CURP123456HDFRPN01", "Ada Lovelace", "ada@utez.edu.mx");

		var claims = service.verify(ticket);

		assertThat(claims.getSubject()).isEqualTo(SUBJECT);
		assertThat(claims.get("curp", String.class)).isEqualTo("CURP123456HDFRPN01");
		assertThat(claims.get("nombre", String.class)).isEqualTo("Ada Lovelace");
		assertThat(claims.get("correo", String.class)).isEqualTo("ada@utez.edu.mx");
	}

	@Test
	void blankProfileFieldsAreOmittedRatherThanStoredEmpty() {
		String ticket = service.issue(SUBJECT, null, "  ", "");

		var claims = service.verify(ticket);

		assertThat(claims.get("curp")).isNull();
		assertThat(claims.get("nombre")).isNull();
		assertThat(claims.get("correo")).isNull();
	}

	@Test
	void tamperedPayloadIsRejected() {
		String ticket = service.issue(SUBJECT, "CURP123456HDFRPN01", "Ada Lovelace", null);
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

		String forged = attacker.issue("attacker-subject", "CURP999999HDFRPN99", "Mallory", null);

		assertThatThrownBy(() -> service.verify(forged)).isInstanceOf(LlaveMxVerificationRequiredException.class);
	}

	@Test
	void expiredTicketIsRejected() {
		LlaveMxVerificationTicketService expiredIssuer = new LlaveMxVerificationTicketService(SECRET,
				Duration.ofMillis(1));
		String ticket = expiredIssuer.issue(SUBJECT, null, null, null);
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
	void cookieIsScopedToTheRegistrationEndpoint() {
		// The ticket is only ever needed by POST /candidates; narrowing the path
		// keeps it from riding along on every other request.
		assertThat(LlaveMxVerificationTicketService.COOKIE_PATH).isEqualTo("/candidates");
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
