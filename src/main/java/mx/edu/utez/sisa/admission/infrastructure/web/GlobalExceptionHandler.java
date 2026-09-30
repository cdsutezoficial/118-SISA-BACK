package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.admission.shared.exception.AmbiguousFichaPaymentConceptException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyExistsException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidCandidateFichaDataException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;
import mx.edu.utez.sisa.admission.shared.exception.TooManyPaymentAccessAttemptsException;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotOpenException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigSalesClosedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Maps the {@code admission} bounded context's domain exceptions
 * ({@link OutreachChannelNotFoundException}, {@link HighSchoolTypeNotFoundException},
 * plus the candidate-registration family: {@code CandidateAlreadyExistsException},
 * {@code ProgramAdmissionConfigNotFoundException} (404),
 * {@code ProgramAdmissionConfigNotOpenException} (409),
 * {@link ProgramAdmissionConfigSalesClosedException} /
 * {@link ProgramAdmissionConfigCapacityReachedException} (409 — the dates and the
 * quota the Configuración de Admisión screen edits, finally enforced),
 * {@code InvalidCandidateFichaDataException} (400),
 * {@code CandidateNotFoundException} (404 — payment-confirmation / ficha-read
 * family) and {@code CandidateAlreadyPaidException} (409 — repeat
 * confirmation) and {@link EvoPaymentGatewayException} (502 — the payment
 * processor is the failing upstream dependency, never a client mistake))
 * to HTTP statuses (same "own {@code @RestControllerAdvice}, additive only"
 * decision as {@code academic_config.GlobalExceptionHandler}). Generic
 * handlers (bean validation, type-mismatch, catch-all) already exist
 * app-globally in {@code identity.GlobalExceptionHandler} and are
 * deliberately NOT redeclared here — a duplicate {@code @ExceptionHandler}
 * for the same exception type across two {@code @RestControllerAdvice} beans
 * is ambiguous.
 *
 * <p>Explicit {@link Component} bean name: this class,
 * {@code academic_config.infrastructure.web.GlobalExceptionHandler} and
 * {@code identity.infrastructure.web.GlobalExceptionHandler} all share the
 * same simple class name, which otherwise collide under Spring's default
 * annotation-derived bean naming — {@code @RestControllerAdvice} itself has
 * no name attribute, so an explicit {@code @Component} name is the
 * disambiguation.
 */
@RestControllerAdvice
@Component("admissionGlobalExceptionHandler")
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * Stable machine-readable discriminators. These are part of the HTTP contract:
	 * the frontend branches on them, so a rename is a breaking change, while the
	 * messages next to them are copy and may be reworded freely.
	 *
	 * <p>The three the registration wizard must tell apart are
	 * {@link #CODE_QUOTA_REACHED}, {@link #CODE_SALES_WINDOW_CLOSED} and
	 * {@link #CODE_CANDIDATE_ALREADY_EXISTS} — all three arrive as 409 and each one
	 * sends the applicant somewhere different.
	 */
	public static final String CODE_QUOTA_REACHED = "ADMISSION_QUOTA_REACHED";
	public static final String CODE_SALES_WINDOW_CLOSED = "ADMISSION_SALES_WINDOW_CLOSED";
	public static final String CODE_CONFIG_NOT_OPEN = "ADMISSION_CONFIG_NOT_OPEN";
	public static final String CODE_PAYMENT_WINDOW_CLOSED = "ADMISSION_PAYMENT_WINDOW_CLOSED";
	public static final String CODE_CANDIDATE_ALREADY_EXISTS = "ADMISSION_CANDIDATE_ALREADY_EXISTS";
	public static final String CODE_CANDIDATE_NOT_FOUND = "ADMISSION_CANDIDATE_NOT_FOUND";
	public static final String CODE_CONFIG_NOT_FOUND = "ADMISSION_CONFIG_NOT_FOUND";
	public static final String CODE_CONCEPT_NOT_FOUND = "ADMISSION_CONCEPT_NOT_FOUND";
	public static final String CODE_CONCEPT_AMBIGUOUS = "ADMISSION_CONCEPT_AMBIGUOUS";
	public static final String CODE_ALREADY_PAID = "ADMISSION_ALREADY_PAID";
	public static final String CODE_FICHA_DATA_INVALID = "ADMISSION_FICHA_DATA_INVALID";
	public static final String CODE_VERIFICATION_INVALID = "ADMISSION_VERIFICATION_INVALID";
	public static final String CODE_EVO_GATEWAY_ERROR = "ADMISSION_EVO_GATEWAY_ERROR";
	public static final String CODE_RATE_LIMITED = "ADMISSION_PAYMENT_ACCESS_RATE_LIMITED";

	/**
	 * A unique index rejected the registration because a concurrent one got there
	 * first — almost always {@code candidate.folio}, whose number comes from
	 * {@code count(prefix) + 1} with no lock. Distinct from
	 * {@link #CODE_CANDIDATE_ALREADY_EXISTS} on purpose: that one means "your CURP
	 * is already registered", which is a fact about the applicant and never
	 * resolves by retrying. This one is transient and always does.
	 */
	public static final String CODE_REGISTRATION_CONFLICT = "ADMISSION_REGISTRATION_CONFLICT";

	@ExceptionHandler(HighSchoolTypeNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleHighSchoolTypeNotFound(HighSchoolTypeNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(ProgramAdmissionConfigNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleProgramAdmissionConfigNotFound(ProgramAdmissionConfigNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, CODE_CONFIG_NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(OutreachChannelNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleOutreachChannelNotFound(OutreachChannelNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(CandidateNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleCandidateNotFound(CandidateNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, CODE_CANDIDATE_NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(CandidateAlreadyExistsException.class)
	public ResponseEntity<ErrorResponse> handleCandidateAlreadyExists(CandidateAlreadyExistsException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_CANDIDATE_ALREADY_EXISTS, ex.getMessage(), request);
	}

	/**
	 * A unique index rejected the write at flush time. Two things can get here, and
	 * the message covers both without naming either:
	 *
	 * <ul>
	 * <li><b>Folio collision (the common one).</b> {@code generateFolio()} is
	 * {@code count(prefix) + 1} — read-then-write with no lock — so two
	 * simultaneous registrations compute the same number and the loser collides on
	 * {@code candidate.folio}'s unique index. Nothing about her data is wrong and
	 * nothing is stored, so "already exists, review your information" would be a
	 * lie: there is nothing to review.</li>
	 * <li><b>CURP collision (rare).</b> The pre-check at
	 * {@code RegisterCandidateUseCaseImpl} is also read-then-write, so the loser
	 * of a same-CURP race can get here instead of the intended
	 * {@code CandidateAlreadyExistsException}. Self-correcting: by then the other
	 * registration is committed, so the retry's pre-check catches it and returns
	 * the proper 409 with the CURP-specific message.</li>
	 * </ul>
	 *
	 * <p>Either way the answer is the same and it is true: nothing was saved, the
	 * applicant keeps her wizard state, and pressing "Finalizar registro" again
	 * succeeds. The wizard stays on its step because the folio was never assigned.
	 *
	 * <p>More specific than {@code DataIntegrityViolationException}, which
	 * {@code identity.GlobalExceptionHandler} maps to a 400 — that covers NOT NULL
	 * / length / FK, this one covers "a concurrent write got there first".
	 */
	@ExceptionHandler(DuplicateKeyException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
		log.warn("Unique constraint violated on {}", request.getRequestURI(), ex);
		return build(HttpStatus.CONFLICT, CODE_REGISTRATION_CONFLICT,
				"No pudimos completar tu registro en este momento. Inténtalo de nuevo en un momento.", request);
	}

	@ExceptionHandler(ProgramAdmissionConfigNotOpenException.class)
	public ResponseEntity<ErrorResponse> handleProgramAdmissionConfigNotOpen(ProgramAdmissionConfigNotOpenException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_CONFIG_NOT_OPEN, ex.getMessage(), request);
	}

	/**
	 * The sales window is closed. The message is applicant-facing (it names the
	 * date, not the config), so it is forwarded as-is rather than replaced with a
	 * generic 409.
	 */
	@ExceptionHandler(ProgramAdmissionConfigSalesClosedException.class)
	public ResponseEntity<ErrorResponse> handleProgramAdmissionConfigSalesClosed(
			ProgramAdmissionConfigSalesClosedException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_SALES_WINDOW_CLOSED, ex.getMessage(), request);
	}

	/**
	 * The quota is full. Shouted with its own code because the wizard uses it to
	 * send the applicant back to the career step with a refreshed list, instead of
	 * telling them to check their CURP.
	 */
	@ExceptionHandler(ProgramAdmissionConfigCapacityReachedException.class)
	public ResponseEntity<ErrorResponse> handleProgramAdmissionConfigCapacityReached(
			ProgramAdmissionConfigCapacityReachedException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_QUOTA_REACHED, ex.getMessage(), request);
	}

	@ExceptionHandler(FichaPaymentConceptNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleFichaPaymentConceptNotFound(FichaPaymentConceptNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_CONCEPT_NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(AmbiguousFichaPaymentConceptException.class)
	public ResponseEntity<ErrorResponse> handleAmbiguousFichaPaymentConcept(AmbiguousFichaPaymentConceptException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_CONCEPT_AMBIGUOUS, ex.getMessage(), request);
	}

	/**
	 * The tuition concept exists but its availability window is closed. 409, and
	 * the message is forwarded verbatim because it is applicant-facing and names
	 * the date that actually matters.
	 */
	@ExceptionHandler(PaymentConceptExpiredException.class)
	public ResponseEntity<ErrorResponse> handlePaymentConceptExpired(PaymentConceptExpiredException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_PAYMENT_WINDOW_CLOSED, ex.getMessage(), request);
	}

	@ExceptionHandler(CandidateAlreadyPaidException.class)
	public ResponseEntity<ErrorResponse> handleCandidateAlreadyPaid(CandidateAlreadyPaidException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_ALREADY_PAID, ex.getMessage(), request);
	}

	@ExceptionHandler(InvalidCandidateFichaDataException.class)
	public ResponseEntity<ErrorResponse> handleInvalidCandidateFichaData(InvalidCandidateFichaDataException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, CODE_FICHA_DATA_INVALID, ex.getMessage(), request);
	}

	@ExceptionHandler(InvalidPaymentVerificationException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPaymentVerification(InvalidPaymentVerificationException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, CODE_VERIFICATION_INVALID, ex.getMessage(), request);
	}

	@ExceptionHandler(EvoPaymentGatewayException.class)
	public ResponseEntity<ErrorResponse> handleEvoPaymentGateway(EvoPaymentGatewayException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_GATEWAY, CODE_EVO_GATEWAY_ERROR, ex.getMessage(), request);
	}

	/**
	 * Per-IP throttle tripped on {@code POST /candidates/payment-access} — see
	 * {@link PaymentAccessRateLimiter}. 429 so the portal can show "espera unos
	 * minutos" instead of a generic error, and so a brute-force script gets an
	 * unambiguous signal to back off.
	 */
	@ExceptionHandler(TooManyPaymentAccessAttemptsException.class)
	public ResponseEntity<ErrorResponse> handleTooManyPaymentAccessAttempts(TooManyPaymentAccessAttemptsException ex,
			HttpServletRequest request) {
		return build(HttpStatus.TOO_MANY_REQUESTS, CODE_RATE_LIMITED, ex.getMessage(), request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		return build(status, null, message, request);
	}

	/**
	 * Builds the error body with a stable {@code code} alongside the
	 * human-readable {@code message}.
	 *
	 * <p>Why both: the wizard has to tell "this career just filled up, go pick
	 * another" apart from "this CURP is already registered" and from "the sales
	 * window closed" — three different recoveries. All three arrive as 409, and
	 * the messages are reworded by copy edits, so branching on the message is a
	 * trap. The code is the contract; the message is for the applicant.
	 */
	private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
			HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI(), code);
		return ResponseEntity.status(status).body(body);
	}
}
