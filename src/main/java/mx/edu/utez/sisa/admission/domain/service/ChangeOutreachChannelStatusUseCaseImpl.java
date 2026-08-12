package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeOutreachChannelStatusUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles an {@code OutreachChannel}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status,
 * mirroring {@code ChangeSubjectClassificationStatusUseCaseImpl}.
 */
public class ChangeOutreachChannelStatusUseCaseImpl implements ChangeOutreachChannelStatusUseCase {

	private final OutreachChannelRepository channelRepository;

	public ChangeOutreachChannelStatusUseCaseImpl(OutreachChannelRepository channelRepository) {
		this.channelRepository = channelRepository;
	}

	@Override
	@Transactional
	public OutreachChannelResult changeStatus(ChangeStatusCommand command) {
		OutreachChannel channel = channelRepository.findById(command.channelId())
				.orElseThrow(() -> new OutreachChannelNotFoundException("Outreach channel not found: " + command.channelId()));

		if (command.target() == OutreachChannelStatus.ACTIVE) {
			channel.activate();
		}
		else {
			channel.deactivate();
		}
		OutreachChannel saved = channelRepository.save(channel);

		return CreateOutreachChannelUseCaseImpl.toResult(saved);
	}
}
