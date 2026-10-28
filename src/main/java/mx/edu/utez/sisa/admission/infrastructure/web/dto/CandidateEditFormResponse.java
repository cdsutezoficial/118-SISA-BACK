package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateEditFormUseCase.CandidateEditForm;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Respuesta de {@code GET /candidates/{id}/edit}.
 *
 * <p>Los bloques de la ficha usan los mismos tipos que {@link RegisterCandidateRequest},
 * con los mismos formatos de texto (fecha {@code dd/MM/yyyy}, horas {@code HH:mm},
 * sexo y estado civil con las etiquetas del formulario). Así el formulario de edición
 * precarga lo que recibe y lo reenvía en el {@code PUT} sin traducir nada.
 */
public record CandidateEditFormResponse(UUID candidateId, String folio, CandidateStatus status,
		boolean llaveMxVerified, String programName, String modality, boolean programChangeAllowed,
		RegisterCandidateRequest.DatosGenerales datosGenerales, RegisterCandidateRequest.Domicilio domicilio,
		RegisterCandidateRequest.Contacto contacto,
		RegisterCandidateRequest.InformacionComplementaria informacionComplementaria,
		RegisterCandidateRequest.Ingresos ingresos, RegisterCandidateRequest.SeleccionCarrera seleccionCarrera,
		RegisterCandidateRequest.AntecedentesEscolares antecedentesEscolares) {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	public static CandidateEditFormResponse from(CandidateEditForm form) {
		RegisterCandidateUseCase.RegisterCandidateCommand f = form.ficha();
		return new CandidateEditFormResponse(form.candidateId(), form.folio(), form.status(), form.llaveMxVerified(),
				form.programName(), form.modality() == null ? null : form.modality().name(),
				form.programChangeAllowed(), datosGenerales(f.datosGenerales()), domicilio(f.domicilio()),
				contacto(f.contacto()), informacionComplementaria(f.informacionComplementaria()),
				ingresos(f.ingresos()), seleccionCarrera(f.seleccionCarrera()),
				antecedentes(f.antecedentesEscolares()));
	}

	private static RegisterCandidateRequest.DatosGenerales datosGenerales(RegisterCandidateUseCase.DatosGenerales d) {
		return new RegisterCandidateRequest.DatosGenerales(d.curp(), d.firstName(), d.lastName1(), d.lastName2(),
				formatDate(d.birthDate()), genderLabel(d.gender()), d.nationality(), d.birthStateId(),
				d.birthMunicipalityId(), d.birthCountry(), d.birthForeignMunicipality(),
				maritalStatusLabel(d.maritalStatus()), d.nativeLanguage(), d.hasChildren(), d.birthForeignState());
	}

	private static RegisterCandidateRequest.Domicilio domicilio(RegisterCandidateUseCase.Domicilio d) {
		if (d == null) {
			return null;
		}
		return new RegisterCandidateRequest.Domicilio(d.street(), d.exteriorNumber(), d.interiorNumber(),
				d.neighborhood(), d.locality(), d.postalCode(), d.stateId(), d.municipalityId());
	}

	private static RegisterCandidateRequest.Contacto contacto(RegisterCandidateUseCase.Contacto c) {
		return new RegisterCandidateRequest.Contacto(c.personalEmail(), c.homePhone(), c.mobilePhone());
	}

	private static RegisterCandidateRequest.InformacionComplementaria informacionComplementaria(
			RegisterCandidateUseCase.InformacionComplementaria i) {
		return new RegisterCandidateRequest.InformacionComplementaria(i.hasPreexistingCondition(),
				i.conditionDescription(), i.hasDisability(), i.disabilityDescription(),
				i.parentsSpeakIndigenousLanguage(), i.parentsIndigenousLanguage(), i.speaksIndigenousLanguage(),
				i.indigenousLanguage(), i.selfIdentifiesIndigenous(), i.selfIdentifiesNonBinary(),
				i.belongsToLgbttiqCommunity(), i.isAfrodescendant(), i.selfIdentifiesAfrodescendant());
	}

	private static RegisterCandidateRequest.Ingresos ingresos(RegisterCandidateUseCase.Ingresos i) {
		BigDecimal family = i.monthlyFamilyIncome();
		return new RegisterCandidateRequest.Ingresos(family, i.isEmployed(), i.employmentType(), i.workPhone(),
				i.monthlyIncome(), i.companyName(), i.jobTitle(), formatTime(i.workStartTime()),
				formatTime(i.workEndTime()));
	}

	private static RegisterCandidateRequest.SeleccionCarrera seleccionCarrera(
			RegisterCandidateUseCase.SeleccionCarrera s) {
		return new RegisterCandidateRequest.SeleccionCarrera(s.admissionConfigId(), s.outreachChannelId(),
				s.isFirstChoice());
	}

	private static RegisterCandidateRequest.AntecedentesEscolares antecedentes(
			RegisterCandidateUseCase.AntecedentesEscolares a) {
		if (a == null) {
			return null;
		}
		// La confirmación de la CCT no se guarda aparte: si quedó confirmada, el
		// formulario la recibe repetida para no pedirla de nuevo.
		return new RegisterCandidateRequest.AntecedentesEscolares(a.schoolName(), a.schoolTypeId(),
				a.studiedInMexico(), a.schoolStateId(), a.schoolMunicipalityId(), a.foreignCountry(), a.schoolCity(),
				a.gpa(), a.cct(), a.cctConfirmed() ? a.cct() : null, a.schoolForeignState());
	}

	private static String formatDate(LocalDate date) {
		return date == null ? null : DATE_FORMATTER.format(date);
	}

	private static String formatTime(LocalTime time) {
		return time == null ? null : TIME_FORMATTER.format(time);
	}

	/** Inversa de {@code CandidateController.toGender}; el formulario solo ofrece dos opciones. */
	private static String genderLabel(Gender gender) {
		if (gender == null) {
			return null;
		}
		return switch (gender) {
			case F -> "Femenino";
			case M -> "Masculino";
			default -> null;
		};
	}

	/** Inversa de {@code CandidateController.toMaritalStatus}, con las etiquetas del formulario. */
	private static String maritalStatusLabel(MaritalStatus status) {
		if (status == null) {
			return null;
		}
		return switch (status) {
			case SOLTERO -> "Soltero/a";
			case CASADO -> "Casado/a";
			case UNION_LIBRE -> "Unión libre";
			case DIVORCIADO -> "Divorciado/a";
			case VIUDO -> "Viudo/a";
			default -> null;
		};
	}
}
