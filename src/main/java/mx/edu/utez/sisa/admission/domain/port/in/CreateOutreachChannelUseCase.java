package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;

import java.util.UUID;

/**
 * Creates an {@code OutreachChannel} catalog entry (docs:
 * {@code 03-admision.md} — "Catálogos"). Role authorization (ADMIN or
 * SERVICIOS_ESCOLARES) is enforced by {@code SecurityFilterConfig}, not
 * here — same convention as {@code CreateSubjectClassificationUseCase}.
 */
public interface CreateOutreachChannelUseCase {

	OutreachChannelResult createChannel(CreateOutreachChannelCommand command);

	/**
	 * @param name deliberately NOT unique — see {@code OutreachChannel}'s javadoc
	 */
	record CreateOutreachChannelCommand(String name) {
	}

	record OutreachChannelResult(UUID id, String name, OutreachChannelStatus status) {
	}
}
