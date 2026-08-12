package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOutreachChannelUseCaseImplTest {

	@Mock
	private OutreachChannelRepository channelRepository;

	private GetOutreachChannelUseCaseImpl useCase;

	private OutreachChannel channel;
	private UUID channelId;

	@BeforeEach
	void setUp() {
		useCase = new GetOutreachChannelUseCaseImpl(channelRepository);
		channel = new OutreachChannel("Facebook");
		channelId = UUID.randomUUID();
		ReflectionTestUtils.setField(channel, "id", channelId);
	}

	@Test
	void getById_returnsTheChannelWhenItExists() {
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));

		OutreachChannelResult result = useCase.getById(channelId);

		assertThat(result.id()).isEqualTo(channelId);
		assertThat(result.name()).isEqualTo("Facebook");
	}

	@Test
	void getById_rejectsUnknownChannelId() {
		UUID unknownId = UUID.randomUUID();
		when(channelRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(OutreachChannelNotFoundException.class);
	}
}
