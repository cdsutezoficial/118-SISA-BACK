package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;

import java.util.UUID;

/**
 * Creates an {@code AcademicDivision} catalog entry (spec: "Create Academic
 * Division"). Role authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced
 * by {@code SecurityFilterConfig}, not here — mirrors how
 * {@code identity.ListUsersUseCase} delegates role checks to the
 * web/security layer instead of a domain-level caller lookup.
 */
public interface CreateAcademicDivisionUseCase {

	AcademicDivisionResult createDivision(CreateAcademicDivisionCommand command);

	/**
	 * @param name             MUST be unique across all divisions
	 * @param code             MUST be unique across all divisions
	 * @param description      free-text description
	 * @param directorPersonId optional — when provided, MUST reference an existing {@code Person}
	 */
	record CreateAcademicDivisionCommand(String name, String code, String description, UUID directorPersonId) {
	}

	/**
	 * Shared result shape reused by {@code UpdateAcademicDivisionUseCase} and
	 * {@code ChangeAcademicDivisionStatusUseCase} — all three return the full
	 * post-operation division state.
	 */
	record AcademicDivisionResult(UUID id, String name, String code, String description, UUID directorPersonId,
			DivisionStatus status) {
	}
}
