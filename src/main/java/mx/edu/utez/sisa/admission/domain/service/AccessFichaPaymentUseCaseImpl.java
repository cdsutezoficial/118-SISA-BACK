package mx.edu.utez.sisa.admission.domain.service;

import java.util.UUID;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Default {@link AccessFichaPaymentUseCase}: resolves {@code folio + CURP
 * suffix} to the payment data the "vuelve a pagar mi ficha" screen needs.
 *
 * <p>Flow: folio (normalized upper-case, trimmed) → candidate → its
 * {@code Person} → compare the last 3 characters of the stored CURP. Any
 * failure raises ONE {@link CandidateNotFoundException} with a single generic
 * message, so a caller cannot tell "no such folio" from "wrong CURP".
 *
 * <p>The comparison is the security-relevant line, so it is written defensively:
 * the stored CURP may be {@code null}/short, and a 3-char suffix is compared
 * with {@link String#regionMatches} on the trimmed, upper-cased tail. No
 * {@code equals} on substrings, no trimming of the caller's suffix beyond
 * whitespace, and no locale surprises.
 */
public class AccessFichaPaymentUseCaseImpl implements AccessFichaPaymentUseCase {

	/** Characters of the CURP the applicant types as the second factor. */
	private static final int CURP_SUFFIX_LENGTH = 3;

	private final CandidateRepository candidateRepository;

	private final CandidatePersonRepository candidatePersonRepository;

	private final FichaPaymentPort fichaPaymentPort;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final FichaAmountResolver fichaAmountResolver;

	private final FichaDisplayPrice fichaDisplayPrice;

	private final Clock clock;

	private final int fichaDeadlineDays;

	/**
	 * @param fichaDisplayPrice prices the ficha from its finance charge, live
	 *                          (tarifa vigente − descuento vigente)
	 */
	public AccessFichaPaymentUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			FichaPaymentPort fichaPaymentPort,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort, FichaAmountResolver fichaAmountResolver,
			FichaDisplayPrice fichaDisplayPrice, Clock clock, int fichaDeadlineDays) {
		this.fichaDisplayPrice = fichaDisplayPrice;
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
		this.fichaPaymentPort = fichaPaymentPort;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.clock = clock;
		this.fichaDeadlineDays = fichaDeadlineDays;
	}

	@Override
	@Transactional(readOnly = true)
		public PaymentAccess access(String folio, String curpSuffix) {
		Candidate candidate = resolveCandidate(folio);
		verifyCurpSuffix(candidate, curpSuffix);
		return paymentAccessOf(candidate);
	}

	@Override
	@Transactional(readOnly = true)
	public PaymentAccess accessById(UUID candidateId) {
		Candidate candidate = candidateId == null ? null : candidateRepository.findById(candidateId).orElse(null);
		if (candidate == null) {
			throw notFound();
		}
		return paymentAccessOf(candidate);
	}

	/** Priced live on every call: the tariff and the discount in force today. */
	private PaymentAccess paymentAccessOf(Candidate candidate) {

		FichaPaymentPort.FichaCharge payment = FichaCharges.of(candidate, fichaPaymentPort)
				.orElseThrow(() -> notFound());

		boolean alreadyPaid = payment.isPaid();
		LocalDate registrationDeadline = payment.registrationDeadline(clock.getZone());
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = programAdmissionConfigQueryPort
				.findById(candidate.getAdmissionConfigId()).orElse(null);

		// Null config is tolerated rather than thrown: this is the recovery path
		// (folio + CURP suffix), and refusing to render a ficha that demonstrably
		// exists would strand the applicant with no way to see what they owe.
		// paymentClosesOn already answers null when there is nothing to report.
		LocalDate paymentClosesOn = config == null ? null : fichaAmountResolver.paymentClosesOn(config.programId());
		// Read live, with the ticket's snapshot only as the fallback: closing a
		// cohort early has to shorten the window the screen promises, and that is a
		// config edit made after the ficha was issued.
		LocalDate processClosesOn = config == null ? registrationDeadline
				: config.closesAt().atZone(clock.getZone()).toLocalDate();
		LocalDate paymentDeadline = visiblePaymentDeadline(candidate, processClosesOn);
		boolean paymentExpired = !FichaPaymentWindow.isPayableOn(candidate, processClosesOn, fichaDeadlineDays,
				LocalDate.now(clock), clock.getZone());

		// The price is the ficha charge's, computed live: what the applicant sees
		// here is what the checkout will charge a moment later (it re-computes it
		// at the click anyway, and never trusts this screen).
		FichaDisplayPrice.Price price = fichaDisplayPrice != null
				? fichaDisplayPrice.of(payment.financePaymentId(), config == null ? null : config.programId())
				: FichaDisplayPrice.Price.NONE;

		return new PaymentAccess(candidate.getId(), candidate.getFolio(),
				fullName(candidatePersonRepository.findById(candidate.getPersonId()).orElse(null)),
				config == null ? null : config.programName(), price.amount(), payment.referenceNumber(),
				registrationDeadline, payment.status(), payment.receiptNumber(),
				// only meaningful once PAID; null keeps "Pendiente" screens honest
				alreadyPaid ? payment.paidAt() : null, alreadyPaid, paymentClosesOn, paymentDeadline,
				candidate.getStatus(), paymentExpired, price.originalAmount(), price.discountAmount(),
				price.discountReason());
	}

	/**
	 * The visible "Fecha límite de pago": the ficha's window, i.e. the earlier of
	 * its own plazo and the day its admission process closes.
	 *
	 * <p>Shares {@link FichaPaymentWindow#deadlineOf} with the sweep and the
	 * checkout gate, so the date this screen promises is the date the engine
	 * enforces. It used to bound the window by the ticket's snapshotted
	 * {@code registrationDeadline} instead: an office that closes a cohort early
	 * would see the portal keep promising payment until the original date, and the
	 * applicant would only learn otherwise from a 409 at the checkout.
	 */
	private LocalDate visiblePaymentDeadline(Candidate candidate, LocalDate processClosesOn) {
		return FichaPaymentWindow.deadlineOf(candidate, processClosesOn, fichaDeadlineDays, clock.getZone());
	}

	private Candidate resolveCandidate(String folio) {
		String normalized = folio == null ? "" : folio.trim().toUpperCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			throw notFound();
		}
		Optional<Candidate> found = candidateRepository.findByFolio(normalized);
		return found.orElseThrow(() -> notFound());
	}

	private void verifyCurpSuffix(Candidate candidate, String curpSuffix) {
		String expected = candidatePersonRepository.findById(candidate.getPersonId()).map(Person::getCurp)
				.map(curp -> curp.trim().toUpperCase(Locale.ROOT)).orElse(null);
		String provided = curpSuffix == null ? "" : curpSuffix.trim().toUpperCase(Locale.ROOT);

		if (expected == null || expected.length() < CURP_SUFFIX_LENGTH
				|| provided.length() != CURP_SUFFIX_LENGTH
				|| !expected.regionMatches(expected.length() - CURP_SUFFIX_LENGTH, provided, 0,
						CURP_SUFFIX_LENGTH)) {
			throw notFound();
		}
	}

	/**
	 * One message for every rejection reason on purpose: distinct "folio not
	 * found" vs "CURP does not match" responses would turn this public
	 * endpoint into a folio-existence oracle.
	 */
	private static CandidateNotFoundException notFound() {
		return new CandidateNotFoundException(
				"No encontramos una ficha de admisión con ese folio y CURP. Verifica tus datos.");
	}

	/**
	 * "Ana Torres Ramos" — a missing second surname must not leave a double or
	 * trailing space ("Ana Torres "), so the joined parts are trimmed at the end.
	 */
	private static String fullName(Person person) {
		if (person == null) {
			return null;
		}
		String name = (join(person.getFirstName()) + join(person.getLastName1()) + join(person.getLastName2())).trim();
		return name.isEmpty() ? null : name;
	}

	private static String join(String value) {
		return value == null || value.isBlank() ? "" : value.trim() + " ";
	}
}
