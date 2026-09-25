package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.shared.model.EmploymentType;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Read query for the admission ficha: the applicant's registration plus her
 * ticket payment and program name. Assembles data owned by different
 * repositories ({@code Candidate}, its {@code Person}, the
 * {@code ADMISSION_FICHA} {@code AdmissionPayment}, and the program name from
 * {@code academic_config}). Governs {@code GET /candidates/{id}}, the ficha
 * PDF and the content of the payment emails — one query the web layer reuses
 * for all three consumers. Missing candidate or payment → empty by design;
 * the web layer maps to HTTP 404.
 *
 * <p>Fase 7 expanded the projection with the full "Paso 4" ficha form so the
 * ficha PDF (Fase 8) mirrors {@code CandidatoRegistro.tsx}. The original flat
 * fields stay untouched (the screen's {@code CandidateFichaResponse} still
 * maps them one-to-one); the new sections live in nested records, resolved
 * catalog ids→names at assemble time via {@code PlaceNameLookupPort} /
 * {@code ProgramAdmissionConfigQueryPort} / the outreach-channel and
 * high-school-type repos.
 */
public interface GetCandidateFichaUseCase {

	FichaData get(UUID candidateId);

	record FichaData(UUID candidateId, String folio, CandidateStatus candidateStatus, Instant registeredAt,
			UUID admissionConfigId, String programName, String curp, String firstName, String lastName1,
			String lastName2, String email, String homePhone, String mobilePhone, String referenceNumber,
			BigDecimal amount, LocalDate deadline, AdmissionPaymentStatus paymentStatus, String receiptNumber,
			Instant paidAt, String orderId, DatosGenerales datosGenerales, Domicilio domicilio,
			InformacionComplementaria informacionComplementaria, Ingresos ingresos, SeleccionCarrera seleccionCarrera,
			AntecedentesEscolares antecedentesEscolares) {

		/** Datos Generales (Paso 1) — identity facts resolved to display labels where catalog-led. */
		public record DatosGenerales(LocalDate birthDate, Gender gender, String nationality, String birthStateName,
				String birthMunicipalityName, MaritalStatus maritalStatus, String nativeLanguage,
				boolean hasChildren) {
		}

		/** Domicilio Actual (Paso 1) — street-level address with state/municipality names. */
		public record Domicilio(String street, String exteriorNumber, String interiorNumber, String neighborhood,
				String locality, String postalCode, String stateName, String municipalityName) {
		}

		/** Información Complementaria (Paso 2) — health + diversity self-identification. */
		public record InformacionComplementaria(boolean hasPreexistingCondition, String conditionDescription,
				boolean hasDisability, String disabilityDescription, boolean parentsSpeakIndigenousLanguage,
				String parentsIndigenousLanguage, boolean speaksIndigenousLanguage, String indigenousLanguage,
				boolean selfIdentifiesIndigenous, boolean selfIdentifiesNonBinary, boolean belongsToLgbttiqCommunity,
				boolean isAfrodescendant, boolean selfIdentifiesAfrodescendant) {
		}

		/** Ingresos (Paso 2) — household/employment data. */
		public record Ingresos(BigDecimal monthlyFamilyIncome, boolean isEmployed, EmploymentType employmentType,
				String workPhone, BigDecimal monthlyIncome, String companyName, String jobTitle, LocalTime workStartTime,
				LocalTime workEndTime) {
		}

		/** Selección de Carrera (Paso 3) — modality/canal/period resolved to names. */
		public record SeleccionCarrera(String modalityName, String outreachChannelName, boolean isFirstChoice,
				String periodName) {
		}

		/** Antecedentes Escolares (Paso 3) — high-school background with type/place names. */
		public record AntecedentesEscolares(String schoolName, String schoolTypeName, boolean studiedInMexico,
				String schoolStateName, String schoolMunicipalityName, String foreignCountry, String schoolCity,
				BigDecimal gpa, String cct) {
		}
	}
}
