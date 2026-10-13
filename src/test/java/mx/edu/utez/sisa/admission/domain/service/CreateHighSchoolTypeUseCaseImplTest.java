package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.CreateHighSchoolTypeCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.shared.exception.DuplicateHighSchoolTypeNameException;
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
class CreateHighSchoolTypeUseCaseImplTest {

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	private CreateHighSchoolTypeUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreateHighSchoolTypeUseCaseImpl(highSchoolTypeRepository);
	}

	@Test
	void createHighSchoolType_successfulCreation() {
		when(highSchoolTypeRepository.findByName("Conalep")).thenReturn(Optional.empty());
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase.createHighSchoolType(new CreateHighSchoolTypeCommand("Conalep"));

		assertThat(result.status()).isEqualTo(HighSchoolTypeStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("Conalep");
	}

	@Test
	void createHighSchoolType_rejectsDuplicateName() {
		// Fase 10 reversió la decisión anterior: el nombre normalizado SÍ es único,
		// también entre activos e inactivos. Este test antes afirmaba lo contrario.
		when(highSchoolTypeRepository.findByName("Conalep"))
				.thenReturn(Optional.of(new HighSchoolType("Conalep")));

		assertThatThrownBy(() -> useCase.createHighSchoolType(new CreateHighSchoolTypeCommand("Conalep")))
				.isInstanceOf(DuplicateHighSchoolTypeNameException.class);
	}

	@Test
	void createHighSchoolType_normalizesBeforeCheckingForDuplicates() {
		// El detalle que hace que la regla funcione: la colación de MySQL distingue
		// el espacio final, así que buscar con el valor crudo dejaría pasar
		// "  Conalep  ". El mock se llama con el nombre ya normalizado.
		when(highSchoolTypeRepository.findByName("Conalep"))
				.thenReturn(Optional.of(new HighSchoolType("Conalep")));

		assertThatThrownBy(() -> useCase.createHighSchoolType(new CreateHighSchoolTypeCommand("  Conalep  ")))
				.isInstanceOf(DuplicateHighSchoolTypeNameException.class);
		verify(highSchoolTypeRepository).findByName("Conalep");
	}

	@Test
	void createHighSchoolType_doesNotSaveWhenNameIsDuplicate() {
		when(highSchoolTypeRepository.findByName("Conalep"))
				.thenReturn(Optional.of(new HighSchoolType("Conalep")));

		assertThatThrownBy(() -> useCase.createHighSchoolType(new CreateHighSchoolTypeCommand("Conalep")))
				.isInstanceOf(DuplicateHighSchoolTypeNameException.class);

		verify(highSchoolTypeRepository, never()).save(any());
	}

	@Test
	void createHighSchoolType_savesTheNormalizedName() {
		when(highSchoolTypeRepository.findByName("Bachillerato Técnico")).thenReturn(Optional.empty());
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase
				.createHighSchoolType(new CreateHighSchoolTypeCommand("  Bachillerato   Técnico "));

		assertThat(result.name()).isEqualTo("Bachillerato Técnico");
	}

	@Test
	void createHighSchoolType_keepsAccentsAndCaseAsTyped() {
		// El nombre se muestra al usuario, así que no se pasa a mayúsculas ni se le
		// quitan los acentos: "Bachillerato Técnico" se guarda tal cual. Que la
		// comparación no distinga mayúsculas ni acentos es cosa de la colación de
		// MySQL, no del valor guardado.
		when(highSchoolTypeRepository.findByName("Bachillerato Técnico")).thenReturn(Optional.empty());
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase
				.createHighSchoolType(new CreateHighSchoolTypeCommand("Bachillerato Técnico"));

		assertThat(result.name()).isEqualTo("Bachillerato Técnico");
	}

	@Test
	void createHighSchoolType_rejectsDuplicateOfAnInactiveType() {
		// La búsqueda no filtra por estado a propósito: un tipo desactivado sigue
		// ocupando su nombre, y los selectores de referencia del registro filtran
		// por estado — dos filas idénticas se verían como dos opciones iguales.
		HighSchoolType inactive = new HighSchoolType("Conalep");
		inactive.deactivate();
		when(highSchoolTypeRepository.findByName("Conalep")).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> useCase.createHighSchoolType(new CreateHighSchoolTypeCommand("Conalep")))
				.isInstanceOf(DuplicateHighSchoolTypeNameException.class);
	}
}
