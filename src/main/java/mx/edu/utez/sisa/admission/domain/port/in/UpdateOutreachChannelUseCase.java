package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;

import java.util.UUID;

/**
 * Updates an existing {@code OutreachChannel}'s {@code name}. {@code status}
 * is deliberately absent from this command — status transitions are the sole
 * responsibility of {@code ChangeOutreachChannelStatusUseCase}.
 *
 * <p>The {@code name} is revalidated for uniqueness by the implementation (Fase
 * 9), excluding the row being edited so saving an unchanged channel is not a
 * conflict — the same self-exclusion {@code UpdateAcademicDivisionUseCase} does.
 */
public interface UpdateOutreachChannelUseCase {

	OutreachChannelResult updateChannel(UpdateOutreachChannelCommand command);

	record UpdateOutreachChannelCommand(UUID channelId, String name) {
	}
}
