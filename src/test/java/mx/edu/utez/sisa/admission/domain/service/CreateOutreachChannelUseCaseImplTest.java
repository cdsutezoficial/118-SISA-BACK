package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.CreateOutreachChannelCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOutreachChannelUseCaseImplTest {

	@Mock
	private OutreachChannelRepository channelRepository;

	private CreateOutreachChannelUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreateOutreachChannelUseCaseImpl(channelRepository);
	}

	@Test
	void createChannel_successfulCreation() {
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase.createChannel(new CreateOutreachChannelCommand("Facebook"));

		assertThat(result.status()).isEqualTo(OutreachChannelStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("Facebook");
	}

	@Test
	void createChannel_allowsDuplicateName() {
		// name is deliberately NOT unique for this aggregate — see
		// OutreachChannel's javadoc and the domain doc
		// (03-admision.md — "Catálogos"). There is no uniqueness check at all
		// (unlike SubjectClassification's code), so two channels with the same
		// name must both succeed.
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult first = useCase.createChannel(new CreateOutreachChannelCommand("Facebook"));
		OutreachChannelResult second = useCase.createChannel(new CreateOutreachChannelCommand("Facebook"));

		assertThat(first.name()).isEqualTo("Facebook");
		assertThat(second.name()).isEqualTo("Facebook");
	}
}
