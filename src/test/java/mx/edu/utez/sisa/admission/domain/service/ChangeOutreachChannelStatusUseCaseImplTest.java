package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeOutreachChannelStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
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
class ChangeOutreachChannelStatusUseCaseImplTest {

	@Mock
	private OutreachChannelRepository channelRepository;

	private ChangeOutreachChannelStatusUseCaseImpl useCase;

	private OutreachChannel channel;
	private UUID channelId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeOutreachChannelStatusUseCaseImpl(channelRepository);
		channel = new OutreachChannel("Facebook");
		channelId = UUID.randomUUID();
		ReflectionTestUtils.setField(channel, "id", channelId);
	}

	@Test
	void changeStatus_deactivatesAnActiveChannel() {
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), channelId, OutreachChannelStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(OutreachChannelStatus.INACTIVE);
	}

	@Test
	void changeStatus_reactivatesAnInactiveChannel() {
		channel.deactivate();
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), channelId, OutreachChannelStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(OutreachChannelStatus.ACTIVE);
	}

	@Test
	void changeStatus_isIdempotentWhenTargetMatchesCurrentStatus() {
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), channelId, OutreachChannelStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(OutreachChannelStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsUnknownChannelId() {
		UUID unknownId = UUID.randomUUID();
		when(channelRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, OutreachChannelStatus.ACTIVE)))
				.isInstanceOf(OutreachChannelNotFoundException.class);
	}
}
