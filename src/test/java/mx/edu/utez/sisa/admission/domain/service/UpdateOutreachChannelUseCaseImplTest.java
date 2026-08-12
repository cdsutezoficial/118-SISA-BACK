package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateOutreachChannelUseCase.UpdateOutreachChannelCommand;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateOutreachChannelUseCaseImplTest {

	@Mock
	private OutreachChannelRepository channelRepository;

	private UpdateOutreachChannelUseCaseImpl useCase;

	private OutreachChannel channel;
	private UUID channelId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateOutreachChannelUseCaseImpl(channelRepository);
		channel = new OutreachChannel("Facebook");
		channelId = UUID.randomUUID();
		ReflectionTestUtils.setField(channel, "id", channelId);
	}

	@Test
	void updateChannel_successfulUpdateLeavesStatusUnchanged() {
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase
				.updateChannel(new UpdateOutreachChannelCommand(channelId, "Feria educativa"));

		assertThat(result.name()).isEqualTo("Feria educativa");
		assertThat(result.status()).isEqualTo(OutreachChannelStatus.ACTIVE);
	}

	@Test
	void updateChannel_allowsKeepingItsOwnCurrentName() {
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase.updateChannel(new UpdateOutreachChannelCommand(channelId, "Facebook"));

		assertThat(result.name()).isEqualTo("Facebook");
	}

	@Test
	void updateChannel_rejectsUnknownChannelId() {
		UUID unknownId = UUID.randomUUID();
		when(channelRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateChannel(new UpdateOutreachChannelCommand(unknownId, "Facebook")))
				.isInstanceOf(OutreachChannelNotFoundException.class);
	}
}
