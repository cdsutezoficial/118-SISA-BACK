package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;

import java.util.UUID;

/**
 * Creates a {@code HighSchoolType} catalog entry. Role authorization (ADMIN
 * or SERVICIOS_ESCOLARES) is enforced by {@code SecurityFilterConfig}, not
 * here — same convention as {@code CreateOutreachChannelUseCase}.
 */
public interface CreateHighSchoolTypeUseCase {

	HighSchoolTypeResult createHighSchoolType(CreateHighSchoolTypeCommand command);

	/**
	 * @param name unique once normalized, across active and inactive rows alike;
	 *             the implementation normalizes it and rejects a duplicate with
	 *             {@link mx.edu.utez.sisa.admission.shared.exception.DuplicateHighSchoolTypeNameException}
	 */
	record CreateHighSchoolTypeCommand(String name) {
	}

	record HighSchoolTypeResult(UUID id, String name, HighSchoolTypeStatus status) {
	}
}
