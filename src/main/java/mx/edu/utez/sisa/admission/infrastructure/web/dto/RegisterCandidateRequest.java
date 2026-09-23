package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for {@code POST /candidates} — the "ficha de admisión"
 * (Screen 4 wizard), mirroring {@code FichaAdmisionCompleta} section for
 * section. Catalog references that the frontend today sends as names
 * ({@code estadoNacimiento}, {@code municipioNacimiento}, {@code estado}/
 * {@code municipio}, {@code estadoPreparatoria}/{@code municipioPreparatoria},
 * {@code tipoBachillerato}, {@code programa}, {@code canal}) are POSTed as
 * {@code UUID} ids instead: {@code birthStateId}/{@code birthMunicipalityId},
 * {@code stateId}/{@code municipalityId},
 * {@code schoolStateId}/{@code schoolMunicipalityId}, {@code schoolTypeId},
 * {@code admissionConfigId}, {@code outreachChannelId} — the portal resolves
 * names to ids via the existing option endpoints before posting.
 * {@code modalidad} is deliberately NOT accepted: modality is defined by the
 * chosen {@code ProgramAdmissionConfig}'s program, not stored on
 * {@code Candidate} ({@code 03-admision.md}).
 *
 * <p>Cross-layer mapping notes:
 * <ul>
 * <li>{@code fechaNacimiento} is the front's {@code dd/MM/yyyy} string —
 * parsed to {@code LocalDate} in the controller, NOT via Jackson (the repo
 * has no date-format configuration and no {@code @JsonFormat} precedent);
 * the LABEL carries the front's own name for MEN-name symmetry. Persisted as
 * {@code Person.birthDate}.</li>
 * <li>{@code sexo} arrives as {@code "Femenino"/"Masculino"/"Hombre"/"Mujer"}
 * — mapped to {@code Gender} (M/F/NB) in the controller, not here.</li>
 * <li>{@code estadoCivil} arrives as {@code "Soltero/a", "Casado/a", "Unión
 * libre", ...} — mapped to {@code MaritalStatus} in the controller.</li>
 * <li>{@code tipoTrabajo} arrives as {"Tiempo completo"/"Medio tiempo"} —
 * mapped to {@code EmploymentType} (PERMANENT/TEMPORARY); only meaningful
 * when {@code trabaja == true}.</li>
 * <li>{@code horaInicio}/{@code horaFin} are the front's {@code HH:mm}
 * strings — parsed to {@code LocalTime} in the controller.</li>
 * <li>{@code cctConfirmacion} is validated (must equal {@code cct}) in the
 * controller and collapsed into {@code cctConfirmed} for
 * {@code HighSchoolBackground}. {@code contacto.personalEmail} feeds
 * {@code Person.personalEmail}; the front's {@code Candidate.email}
 * top-level field is not part of this DTO's shape.</li>
 * </ul>
 *
 * <p>{@code llaveMxVerified} is accepted and stored as sent (manual-first
 * stage, see {@code RegisterCandidateUseCase}'s javadoc) — the frontend may
 * send {@code false}.
 *
 * <p>The {@code admissionConfigId} must reference a {@code ProgramAdmissionConfig}
 * with status {@code OPEN} (validated by the use case); a non-null
 * {@code outreachChannelId} must reference an existing
 * {@code OutreachChannel} and a non-null {@code schoolTypeId} an existing
 * {@code HighSchoolType}.
 */
public record RegisterCandidateRequest(
		@NotNull DatosGenerales datosGenerales,
		@NotNull Domicilio domicilio,
		@NotNull Contacto contacto,
		@NotNull InformacionComplementaria informacionComplementaria,
		@NotNull Ingresos ingresos,
		@NotNull SeleccionCarrera seleccionCarrera,
		@NotNull AntecedentesEscolares antecedentesEscolares,
		boolean llaveMxVerified) {

	/**
	 * Paso 1 — "Datos Generales". {@code curp} uniqueness is validated by the
	 * use case (409). {@code birthStateId}/{@code birthMunicipalityId}
	 * reference the shared INEGI catalogs; foreign-birth applicants send
	 * {@code paisNacimiento}/{@code ciudadNacimiento} instead.
	 */
	public record DatosGenerales(@NotBlank String curp, @NotBlank String nombres, @NotBlank String apellidoPaterno,
			String apellidoMaterno, @NotBlank String fechaNacimiento, @NotBlank String sexo,
			@NotBlank String nacionalidad, UUID birthStateId, UUID birthMunicipalityId, String paisNacimiento,
			String ciudadNacimiento, @NotBlank String estadoCivil, String lenguaNatal, boolean tieneHijos) {
	}

	/** Paso 1 — "Domicilio Actual" (Mexicana branch, mirroring {@code Address} mexican fields). */
	public record Domicilio(@NotBlank String calle, @NotBlank String numeroExterior, String numeroInterior,
			String colonia, String localidad, @NotBlank String codigoPostal, @NotNull UUID stateId,
			@NotNull UUID municipalityId) {
	}

	/** Paso 1 — "Contacto". {@code personalEmail} feeds {@code Person.personalEmail}. */
	public record Contacto(@NotBlank String personalEmail, String telefonoCasa, String celular) {
	}

	/** Paso 2 — "Información Complementaria" ({@code HealthProfile} + {@code DiversityProfile}, flattened). */
	public record InformacionComplementaria(boolean tieneEnfermedadPreexistente, String descripcionEnfermedad,
			boolean tieneDiscapacidad, String descripcionDiscapacidad, boolean padresHablanLenguaIndigena,
			String lenguaIndigenaPadres, boolean hablaLenguaIndigena, String lenguaIndigenaPropia,
			boolean seIdentificaIndigena, boolean seIdentificaNoBinario, boolean perteneceComunidadLgbttiq,
			boolean esAfrodescendiente, Boolean seIdentificaAfrodescendiente) {
	}

	/** Paso 2 — "Ingresos" ({@code monthlyFamilyIncome} on {@code Person} + {@code EmploymentInfo}). */
	public record Ingresos(@NotNull BigDecimal ingresoMensualFamiliar, boolean trabaja, String tipoTrabajo,
			String telefonoTrabajo, BigDecimal ingresoMensual, String nombreEmpresa, String puesto, String horaInicio,
			String horaFin) {
	}

	/**
	 * Paso 3 — "Selección de Carrera". {@code admissionConfigId} = the chosen
	 * {@code ProgramAdmissionConfig} (defines program + destination period +
	 * generation); {@code outreachChannelId} the difussion channel; both
	 * referenced by {@code UUID}, see class javadoc. {@code modalidad} is
	 * intentionally absent.
	 */
	public record SeleccionCarrera(@NotNull UUID admissionConfigId, UUID outreachChannelId, boolean isFirstChoice) {
	}

	/** Paso 3 — "Antecedentes Escolares" ({@code HighSchoolBackground}). */
	public record AntecedentesEscolares(@NotBlank String nombrePreparatoria, UUID schoolTypeId,
			boolean estudioBachilleratoEnMexico, UUID schoolStateId, UUID schoolMunicipalityId, String paisPreparatoria,
			String ciudadPreparatoria, BigDecimal promedio, String cct, String cctConfirmacion) {
	}
}