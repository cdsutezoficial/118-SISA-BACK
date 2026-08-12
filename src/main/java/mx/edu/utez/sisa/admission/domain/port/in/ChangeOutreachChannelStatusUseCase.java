package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;

import java.util.UUID;

/**
 * Toggles an {@code OutreachChannel}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status,
 * mirroring {@code ChangeSubjectClassificationStatusUseCase}.
 */
public interface ChangeOutreachChannelStatusUseCase {

	OutreachChannelResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId  reserved for future audit-log attribution; not consulted in this slice —
	 *                  mirrors {@code ChangeSubjectClassificationStatusUseCase.ChangeStatusCommand}
	 * @param channelId the channel whose status is transitioning
	 * @param target    the desired {@link OutreachChannelStatus} — {@code ACTIVE} or {@code INACTIVE}
	 */
	record ChangeStatusCommand(UUID callerId, UUID channelId, OutreachChannelStatus target) {
	}
}
