package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateOutreachChannelUseCase.UpdateOutreachChannelCommand;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.DuplicateOutreachChannelNameException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
		when(channelRepository.findByName("Feria educativa")).thenReturn(Optional.empty());
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase
				.updateChannel(new UpdateOutreachChannelCommand(channelId, "Feria educativa"));

		assertThat(result.name()).isEqualTo("Feria educativa");
		assertThat(result.status()).isEqualTo(OutreachChannelStatus.ACTIVE);
	}

	@Test
	void updateChannel_allowsKeepingItsOwnCurrentName() {
		// El self-exclusión: la búsqueda por nombre devuelve la propia fila y el
		// filtro por id la descarta. Sin ese filtro, guardar sin cambios desde la
		// lista devolvería 409 — que es justo el caso que este test cubre.
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.findByName("Facebook")).thenReturn(Optional.of(channel));
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase.updateChannel(new UpdateOutreachChannelCommand(channelId, "Facebook"));

		assertThat(result.name()).isEqualTo("Facebook");
	}

	@Test
	void updateChannel_allowsKeepingItsOwnNameWithDifferentCase() {
		// La colación no distingue mayúsculas, así que la búsqueda devuelve la
		// propia fila y el filtro por id tiene que seguir descartándola.
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.findByName("facebook")).thenReturn(Optional.of(channel));
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase.updateChannel(new UpdateOutreachChannelCommand(channelId, "facebook"));

		assertThat(result.name()).isEqualTo("facebook");
	}

	@Test
	void updateChannel_allowsKeepingItsOwnNameWithSurroundingWhitespace() {
		// " Facebook " se normaliza a "Facebook" antes de buscar, así que vuelve a
		// dar la propia fila.
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.findByName("Facebook")).thenReturn(Optional.of(channel));
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase
				.updateChannel(new UpdateOutreachChannelCommand(channelId, "  Facebook  "));

		assertThat(result.name()).isEqualTo("Facebook");
	}

	@Test
	void updateChannel_rejectsAnotherChannelWithTheSameName() {
		OutreachChannel other = new OutreachChannel("Facebook");
		ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.findByName("Instagram")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> useCase.updateChannel(new UpdateOutreachChannelCommand(channelId, "Instagram")))
				.isInstanceOf(DuplicateOutreachChannelNameException.class);
		verify(channelRepository, never()).save(any());
	}

	@Test
	void updateChannel_rejectsDuplicateOfAnInactiveChannel() {
		// Igual que en create: la unicidad abarca activos e inactivos.
		OutreachChannel inactive = new OutreachChannel("Instagram");
		inactive.deactivate();
		ReflectionTestUtils.setField(inactive, "id", UUID.randomUUID());
		when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
		when(channelRepository.findByName("Instagram")).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> useCase.updateChannel(new UpdateOutreachChannelCommand(channelId, "Instagram")))
				.isInstanceOf(DuplicateOutreachChannelNameException.class);
	}

	@Test
	void updateChannel_rejectsUnknownChannelId() {
		UUID unknownId = UUID.randomUUID();
		when(channelRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateChannel(new UpdateOutreachChannelCommand(unknownId, "Facebook")))
				.isInstanceOf(OutreachChannelNotFoundException.class);
	}
}
