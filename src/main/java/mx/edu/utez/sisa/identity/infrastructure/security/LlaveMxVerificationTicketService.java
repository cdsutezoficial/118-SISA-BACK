package mx.edu.utez.sisa.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort.LlaveMxPrefill;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxVerificationRequiredException;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Short-lived proof that a candidate proved their identity with Llave Morelos.
 *
 * <p>Needed because a redirect cannot carry a server-side fact: when the
 * provider calls back there is no session to attach anything to, and the
 * applicant then fills in a long public registration form. This ticket is what
 * survives the hop — signed by the backend, so the client cannot mint one, and
 * scoped to a couple of minutes, so a leaked URL is worthless by the time a
 * form is filled.
 *
 * <p>Deliberately a separate service instead of a second {@code JwtService}:
 * this token is never an access token, must never be accepted by the filter
 * chain, and has a different audience and lifetime. The {@code typ} claim plus
 * the dedicated sign/verify pair keep the two token families apart.
 *
 * <p>Reuses the JWT secret so there is a single key to rotate.
 */
@Component
public class LlaveMxVerificationTicketService {

	private static final String TYPE_CLAIM = "typ";
	private static final String VERIFICATION_TYPE = "llave-verification";
	private static final String CURP_CLAIM = "curp";
	private static final String NAME_CLAIM = "nombre";
	private static final String EMAIL_CLAIM = "correo";
	private static final String NAMES_CLAIM = "nombres";
	private static final String PATERNAL_SURNAME_CLAIM = "apellidoPaterno";
	private static final String MATERNAL_SURNAME_CLAIM = "apellidoMaterno";
	private static final String BIRTH_DATE_CLAIM = "fechaNacimiento";
	private static final String SEX_CLAIM = "sexo";
	private static final String BIRTH_STATE_CLAIM = "estadoNacimiento";
	private static final String BIRTH_MUNICIPALITY_CLAIM = "municipioNacimiento";
	private static final String PHONE_CLAIM = "celular";

	/**
	 * Cookie carrying the ticket between the provider callback and the
	 * registration POST. Public because the controller that reads it (candidate
	 * registration) lives in another module.
	 */
	public static final String COOKIE_NAME = "sisa.llave.verified";

	/**
	 * Root rather than a single endpoint: the ticket is read by two of them —
	 * {@code POST /candidates} when the ficha is saved and
	 * {@code GET /auth/llave-mx/prefill} when the wizard asks what LlaveMX said
	 * about this person. Scoping it to either one would make the other silently
	 * see no ticket, and there is nothing else to protect against: the value is
	 * HttpOnly, signed, short-lived, and only ever meaningful to this origin.
	 */
	public static final String COOKIE_PATH = "/";

	private final SecretKey key;
	private final Duration ttl;

	public LlaveMxVerificationTicketService(@Value("${sisa.security.jwt.secret}") String secret,
			@Value("${sisa.llave-mx.verification-ticket-ttl:PT10M}") Duration ttl) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.ttl = ttl;
	}

	/**
	 * @param subject the provider's stable OAuth subject — the only field
	 *                trusted; everything else is profile context
	 * @param prefill the fields the wizard locks and pre-fills; may be null when
	 *                the provider returned no person payload worth keeping
	 */
	public String issue(String subject, String curp, String fullName, String institutionalEmail,
			LlaveMxPrefill prefill) {
		Instant now = Instant.now();
		JwtBuilder builder = Jwts.builder()
				.subject(subject)
				.claim(TYPE_CLAIM, VERIFICATION_TYPE)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(ttl)));
		addIfPresent(builder, CURP_CLAIM, curp);
		addIfPresent(builder, NAME_CLAIM, fullName);
		addIfPresent(builder, EMAIL_CLAIM, institutionalEmail);
		if (prefill != null) {
			addIfPresent(builder, NAMES_CLAIM, prefill.nombres());
			addIfPresent(builder, PATERNAL_SURNAME_CLAIM, prefill.apellidoPaterno());
			addIfPresent(builder, MATERNAL_SURNAME_CLAIM, prefill.apellidoMaterno());
			addIfPresent(builder, BIRTH_DATE_CLAIM, prefill.fechaNacimiento());
			addIfPresent(builder, SEX_CLAIM, prefill.sexo());
			addIfPresent(builder, BIRTH_STATE_CLAIM, prefill.estadoNacimiento());
			addIfPresent(builder, BIRTH_MUNICIPALITY_CLAIM, prefill.municipioNacimiento());
			addIfPresent(builder, PHONE_CLAIM, prefill.celular());
		}
		return builder.signWith(key).compact();
	}

	/**
	 * Rebuilds the prefill from a verified ticket, or {@code null} when the
	 * ticket predates the prefill feature (no prefill claim at all) — the wizard
	 * then simply leaves those fields for the applicant to type.
	 *
	 * <p>Blanks are normalized to {@code ""} rather than left null so the caller
	 * never has to branch twice on "absent" versus "present but empty": to the
	 * form both mean "not provided, keep it editable".
	 */
	public LlaveMxPrefill prefillOf(Claims claims) {
		String nombres = claims.get(NAMES_CLAIM, String.class);
		String apellidoPaterno = claims.get(PATERNAL_SURNAME_CLAIM, String.class);
		String apellidoMaterno = claims.get(MATERNAL_SURNAME_CLAIM, String.class);
		String fechaNacimiento = claims.get(BIRTH_DATE_CLAIM, String.class);
		String sexo = claims.get(SEX_CLAIM, String.class);
		String estadoNacimiento = claims.get(BIRTH_STATE_CLAIM, String.class);
		String municipioNacimiento = claims.get(BIRTH_MUNICIPALITY_CLAIM, String.class);
		String email = claims.get(EMAIL_CLAIM, String.class);
		String celular = claims.get(PHONE_CLAIM, String.class);
		if (nombres == null && apellidoPaterno == null && apellidoMaterno == null && fechaNacimiento == null
				&& sexo == null && estadoNacimiento == null && municipioNacimiento == null && celular == null) {
			return null;
		}
		return new LlaveMxPrefill(orEmpty(nombres), orEmpty(apellidoPaterno), orEmpty(apellidoMaterno),
				orEmpty(fechaNacimiento), orEmpty(sexo), orEmpty(estadoNacimiento), orEmpty(municipioNacimiento),
				orEmpty(email), orEmpty(celular));
	}

	/**
	 * The CURP the provider attested, or {@code null} when the ticket carries
	 * none. Separate from {@link #prefillOf(Claims)} because it is not a field
	 * the wizard pre-fills but the key that binds this ticket to a ficha: it is
	 * read even when there is nothing to pre-fill at all.
	 */
	public String curpOf(Claims claims) {
		return claims.get(CURP_CLAIM, String.class);
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Verifies signature, audience type and expiry in one step.
	 *
	 * <p>Every rejection here is {@link LlaveMxVerificationRequiredException}
	 * (→ 403), never an integration failure (→ 502). The distinction is not
	 * cosmetic: a missing, tampered or expired ticket is the applicant's
	 * own round that went wrong and the fix is to verify again, while 502 would
	 * tell them — and any outage alert watching for 5xx — that LlaveMX itself is
	 * down. Reporting the caller's bad credential as a provider outage is how a
	 * real incident gets masked by routine re-verifications.
	 *
	 * @throws LlaveMxVerificationRequiredException when the ticket is absent,
	 *                                              tampered with, of another
	 *                                              type, or expired
	 */
	public Claims verify(String ticket) {
		if (ticket == null || ticket.isBlank()) {
			throw new LlaveMxVerificationRequiredException("Primero debes verificar tu identidad con Llave Morelos.");
		}
		Claims claims;
		try {
			claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(ticket).getPayload();
		} catch (Exception e) {
			// Signature invalid, malformed, or expired. Logged without the token
			// itself — a JWT in a log is a credential in a log.
			throw new LlaveMxVerificationRequiredException(
					"La verificacion con Llave Morelos expiro. Vuelve a verificarla para registrar tu ficha.");
		}
		if (!VERIFICATION_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
			throw new LlaveMxVerificationRequiredException("La verificacion con Llave Morelos no es valida.");
		}
		return claims;
	}

	private static void addIfPresent(JwtBuilder builder, String claim, String value) {
		if (value != null && !value.isBlank()) {
			builder.claim(claim, value);
		}
	}
}
