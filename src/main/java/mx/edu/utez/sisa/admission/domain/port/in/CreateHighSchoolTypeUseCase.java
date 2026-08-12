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
	 * @param name deliberately NOT unique — see {@code HighSchoolType}'s javadoc
	 */
	record CreateHighSchoolTypeCommand(String name) {
	}

	record HighSchoolTypeResult(UUID id, String name, HighSchoolTypeStatus status) {
	}
}
