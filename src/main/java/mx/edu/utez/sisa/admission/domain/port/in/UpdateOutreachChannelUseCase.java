package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;

import java.util.UUID;

/**
 * Updates an existing {@code OutreachChannel}'s {@code name}. {@code status}
 * is deliberately absent from this command — status transitions are the sole
 * responsibility of {@code ChangeOutreachChannelStatusUseCase}. No
 * uniqueness to revalidate (unlike {@code UpdateSubjectClassificationUseCase}'s
 * {@code code}) since {@code name} carries no uniqueness constraint on this
 * aggregate.
 */
public interface UpdateOutreachChannelUseCase {

	OutreachChannelResult updateChannel(UpdateOutreachChannelCommand command);

	record UpdateOutreachChannelCommand(UUID channelId, String name) {
	}
}
