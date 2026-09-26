package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

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

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	public AccessFichaPaymentUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			AdmissionPaymentRepository admissionPaymentRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort) {
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
	}

	@Override
	@Transactional(readOnly = true)
	public PaymentAccess access(String folio, String curpSuffix) {
		Candidate candidate = resolveCandidate(folio);
		verifyCurpSuffix(candidate, curpSuffix);

		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidate.getId())
				.orElseThrow(() -> notFound());

		boolean alreadyPaid = payment.getPaymentStatus() == AdmissionPaymentStatus.PAID;
		String programName = programAdmissionConfigQueryPort.findById(candidate.getAdmissionConfigId())
				.map(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo::programName)
				.orElse(null);

		return new PaymentAccess(candidate.getId(), candidate.getFolio(),
				fullName(candidatePersonRepository.findById(candidate.getPersonId()).orElse(null)), programName,
				payment.getAmount(), payment.getReferenceNumber(), payment.getPaymentDeadline(),
				payment.getPaymentStatus(), payment.getReceiptNumber(),
				// only meaningful once PAID; null keeps "Pendiente" screens honest
				alreadyPaid ? payment.getPaidAt() : null, alreadyPaid);
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
