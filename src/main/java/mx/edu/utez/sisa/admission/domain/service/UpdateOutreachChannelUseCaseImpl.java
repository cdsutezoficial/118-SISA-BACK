package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code OutreachChannel}'s {@code name}. No uniqueness
 * to revalidate — deliberately, unlike
 * {@code UpdateSubjectClassificationUseCaseImpl}'s {@code code} check.
 */
public class UpdateOutreachChannelUseCaseImpl implements UpdateOutreachChannelUseCase {

	private final OutreachChannelRepository channelRepository;

	public UpdateOutreachChannelUseCaseImpl(OutreachChannelRepository channelRepository) {
		this.channelRepository = channelRepository;
	}

	@Override
	@Transactional
	public OutreachChannelResult updateChannel(UpdateOutreachChannelCommand command) {
		OutreachChannel channel = channelRepository.findById(command.channelId())
				.orElseThrow(() -> new OutreachChannelNotFoundException("Outreach channel not found: " + command.channelId()));

		channel.updateDetails(command.name());
		OutreachChannel saved = channelRepository.save(channel);

		return CreateOutreachChannelUseCaseImpl.toResult(saved);
	}
}
