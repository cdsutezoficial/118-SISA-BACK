package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.in.GetOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code OutreachChannel} by id, 404 if missing — same
 * pattern as {@code GetSubjectClassificationUseCaseImpl}.
 */
public class GetOutreachChannelUseCaseImpl implements GetOutreachChannelUseCase {

	private final OutreachChannelRepository channelRepository;

	public GetOutreachChannelUseCaseImpl(OutreachChannelRepository channelRepository) {
		this.channelRepository = channelRepository;
	}

	@Override
	public OutreachChannelResult getById(UUID id) {
		OutreachChannel channel = channelRepository.findById(id)
				.orElseThrow(() -> new OutreachChannelNotFoundException("Outreach channel not found: " + id));
		return CreateOutreachChannelUseCaseImpl.toResult(channel);
	}
}
