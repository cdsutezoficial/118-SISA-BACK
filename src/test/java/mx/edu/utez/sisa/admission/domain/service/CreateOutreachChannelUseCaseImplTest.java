package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.CreateOutreachChannelCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.DuplicateOutreachChannelNameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
		when(channelRepository.findByName("Facebook")).thenReturn(Optional.empty());
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase.createChannel(new CreateOutreachChannelCommand("Facebook"));

		assertThat(result.status()).isEqualTo(OutreachChannelStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("Facebook");
	}

	@Test
	void createChannel_rejectsDuplicateName() {
		// Fase 9 reversió la decisión anterior: el nombre normalizado SÍ es
		// único, también entre activos e inactivos. Este test antes afirmaba lo
		// contrario y ahora afirma la regla del plan de 2026-10-02.
		when(channelRepository.findByName("Facebook"))
				.thenReturn(Optional.of(new OutreachChannel("Facebook")));

		assertThatThrownBy(() -> useCase.createChannel(new CreateOutreachChannelCommand("Facebook")))
				.isInstanceOf(DuplicateOutreachChannelNameException.class);
	}

	@Test
	void createChannel_normalizesBeforeCheckingForDuplicates() {
		// El detalle que hace que la regla funcione: se normaliza ANTES de buscar,
		// porque la consulta de MySQL distingue el espacio final. Si se buscara con
		// el valor crudo, " Facebook " no encontraría nada y el duplicado entraría.
		when(channelRepository.findByName("Facebook"))
				.thenReturn(Optional.of(new OutreachChannel("Facebook")));

		assertThatThrownBy(() -> useCase.createChannel(new CreateOutreachChannelCommand("  Facebook  ")))
				.isInstanceOf(DuplicateOutreachChannelNameException.class);
		verify(channelRepository).findByName("Facebook");
	}

	@Test
	void createChannel_rejectsDuplicateRegardlessOfSurroundingWhitespace() {
		when(channelRepository.findByName("Facebook"))
				.thenReturn(Optional.of(new OutreachChannel("Facebook")));

		assertThatThrownBy(() -> useCase.createChannel(new CreateOutreachChannelCommand("Facebook   ")))
				.isInstanceOf(DuplicateOutreachChannelNameException.class);
	}

	@Test
	void createChannel_doesNotSaveWhenNameIsDuplicate() {
		when(channelRepository.findByName("Facebook"))
				.thenReturn(Optional.of(new OutreachChannel("Facebook")));

		assertThatThrownBy(() -> useCase.createChannel(new CreateOutreachChannelCommand("Facebook")))
				.isInstanceOf(DuplicateOutreachChannelNameException.class);

		// La excepción tiene que salir ANTES de tocar la base: si se guardara y
		// luego se comprobara, el 409 dejaría una fila basura detrás.
		verify(channelRepository, never()).save(any());
	}

	@Test
	void createChannel_savesTheNormalizedName() {
		when(channelRepository.findByName("Referido familiar")).thenReturn(Optional.empty());
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase
				.createChannel(new CreateOutreachChannelCommand("  Referido   familiar  "));

		assertThat(result.name()).isEqualTo("Referido familiar");
	}

	@Test
	void createChannel_keepsTheCaseTheUserTyped() {
		// El nombre es texto visible: se muestra en la lista y en los selectores,
		// así que NO se pasa a mayúsculas aunque la comparación no distinga. Por eso
		// la búsqueda del mock es insensible a mayúsculas y aun así el resultado
		// conserva lo tecleado.
		when(channelRepository.findByName("facebook")).thenReturn(Optional.empty());
		when(channelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		OutreachChannelResult result = useCase.createChannel(new CreateOutreachChannelCommand("facebook"));

		assertThat(result.name()).isEqualTo("facebook");
	}

	@Test
	void createChannel_rejectsDuplicateOfAnInactiveChannel() {
		// La búsqueda no filtra por estado a propósito: un canal desactivado sigue
		// ocupando su nombre. Este test lo fija, porque es la mitad de la regla que
		// el plan pide y la que se rompería sin querer si alguien "optimiza" la
		// consulta filtrando por ACTIVE.
		OutreachChannel inactive = new OutreachChannel("Facebook");
		inactive.deactivate();
		when(channelRepository.findByName("Facebook")).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> useCase.createChannel(new CreateOutreachChannelCommand("Facebook")))
				.isInstanceOf(DuplicateOutreachChannelNameException.class);
	}
}
