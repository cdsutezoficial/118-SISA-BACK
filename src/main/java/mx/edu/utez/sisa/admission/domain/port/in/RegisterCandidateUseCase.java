package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;

import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Registers a candidate's admission ticket ("ficha de admisión") in a single
 * transaction (project stage: "la ficha que llena el estudiante" —
 * {@code POST /candidates}). The command mirrors the frontend ficha
 * ({@code FichaAdmisionCompleta}) section by section, but references every
 * catalog (program, outreach channel, states/municipalities, high-school
 * type) by {@code UUID} — the frontend resolves names to ids via the
 * existing option endpoints before posting.
 *
 * <p>This is the public, unauthenticated registration entry point; security
 * is the web layer's concern ({@code SecurityFilterConfig} grants
 * {@code POST /candidates} to anonymous users), not this port's.
 *
 * <p>TEMPORARY DEVIATION (manual-first stage): the domain invariant
 * "candidate must have {@code llaveMxVerified = true} to register"
 * ({@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md}) is NOT yet
 * enforced — {@code llaveMxVerified} is accepted and stored as sent. Manual
 * registration precedes the LlaveMX integration; enforcement lands with
 * {@code AuthenticateCandidatePortalUseCase}.
 */
public interface RegisterCandidateUseCase {

	CandidateRegistrationResult register(RegisterCandidateCommand command);

	record RegisterCandidateCommand(DatosGenerales datosGenerales, Domicilio domicilio, Contacto contacto,
			InformacionComplementaria informacionComplementaria, Ingresos ingresos,
			AntecedentesEscolares antecedentesEscolares, SeleccionCarrera seleccionCarrera, boolean llaveMxVerified) {
	}

	record DatosGenerales(String curp, String firstName, String lastName1, String lastName2, LocalDate birthDate,
			Gender gender, String nationality, UUID birthStateId, UUID birthMunicipalityId, String birthForeignState,
			String birthForeignMunicipality, MaritalStatus maritalStatus, String nativeLanguage, boolean hasChildren) {
	}

	record Domicilio(String street, String exteriorNumber, String interiorNumber, String neighborhood, String locality,
			String postalCode, UUID stateId, UUID municipalityId) {
	}

	record Contacto(String personalEmail, String homePhone, String mobilePhone) {
	}

	record InformacionComplementaria(boolean hasPreexistingCondition, String conditionDescription,
			boolean hasDisability, String disabilityDescription, boolean parentsSpeakIndigenousLanguage,
			String parentsIndigenousLanguage, boolean speaksIndigenousLanguage, String indigenousLanguage,
			boolean selfIdentifiesIndigenous, boolean selfIdentifiesNonBinary, boolean belongsToLgbttiqCommunity,
			boolean isAfrodescendant, boolean selfIdentifiesAfrodescendant) {
	}

	record Ingresos(BigDecimal monthlyFamilyIncome, boolean isEmployed, String employmentType,
			String workPhone, BigDecimal monthlyIncome, String companyName, String jobTitle, LocalTime workStartTime,
			LocalTime workEndTime) {
	}

	record AntecedentesEscolares(String schoolName, UUID schoolTypeId, boolean studiedInMexico, UUID schoolStateId,
			UUID schoolMunicipalityId, String foreignCountry, String schoolCity, BigDecimal gpa, String cct,
			boolean cctConfirmed) {
	}

	record SeleccionCarrera(UUID admissionConfigId, UUID outreachChannelId, boolean isFirstChoice) {
	}

	record CandidateRegistrationResult(UUID id, UUID personId, UUID admissionConfigId, String folio,
			CandidateStatus status, boolean llaveMxVerified, Instant registeredAt, boolean isFirstChoice,
			UUID outreachChannelId, boolean isEnabledForInduction, FichaPayment payment) {
	}

	/**
	 * The admission-ticket payment generated together with the candidate.
	 *
	 * <p>Two dates, because the screen used to show one under the label "Fecha
	 * límite de pago" and it was neither the payment deadline nor enforceable:
	 *
	 * <ul>
	 * <li>{@code registrationDeadline} — the sales window's closing day,
	 * snapshotted at registration from {@code ProgramAdmissionConfig.closesAt}.
	 * By the time anyone reads this response the applicant is already registered,
	 * so this date is history: it explains why the ficha stops being issuable,
	 * not when to pay.</li>
	 * <li>{@code paymentClosesOn} — the tuition concept's
	 * {@code availableUntil}, read live. This is the one that constrains the
	 * payment, and the one the payment path enforces.</li>
	 * </ul>
	 *
	 * <p>{@code paymentClosesOn} is {@code null} when the catalog sets no closing
	 * date, which the screen must render as "no deadline" rather than as a
	 * placeholder date.
	 *
	 * <p>{@code paymentDeadline} is the date the screen states for "Fecha límite
	 * de pago": the earlier of {@code registrationDeadline} and the ficha's own
	 * {@code registeredAt} + N-day plazo. It deliberately does not borrow
	 * {@code paymentClosesOn}: the concept's {@code availableUntil} can move with
	 * the catalog and is not a date the applicant is asked to act on.
	 *
	 * <p>Mirrors the frontend ficha's "Monto a Pagar / Referencia" (screen 13).
	 */
	record FichaPayment(String referenceNumber, BigDecimal amount, LocalDate registrationDeadline,
			AdmissionPaymentStatus paymentStatus, LocalDate paymentClosesOn, LocalDate paymentDeadline) {
	}
}