package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an {@code OutreachChannel} catalog entry. No uniqueness check on
 * {@code name} — deliberately, per the domain doc (see
 * {@code OutreachChannel}'s javadoc); unlike
 * {@code CreateSubjectClassificationUseCaseImpl}, there is no
 * {@code code}-equivalent field to validate.
 */
public class CreateOutreachChannelUseCaseImpl implements CreateOutreachChannelUseCase {

	private final OutreachChannelRepository channelRepository;

	public CreateOutreachChannelUseCaseImpl(OutreachChannelRepository channelRepository) {
		this.channelRepository = channelRepository;
	}

	@Override
	@Transactional
	public OutreachChannelResult createChannel(CreateOutreachChannelCommand command) {
		OutreachChannel channel = new OutreachChannel(command.name());
		OutreachChannel saved = channelRepository.save(channel);

		return toResult(saved);
	}

	static OutreachChannelResult toResult(OutreachChannel channel) {
		return new OutreachChannelResult(channel.getId(), channel.getName(), channel.getStatus());
	}
}
