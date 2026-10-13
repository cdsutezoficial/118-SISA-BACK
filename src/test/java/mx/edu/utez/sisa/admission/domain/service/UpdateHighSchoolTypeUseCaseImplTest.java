package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateHighSchoolTypeUseCase.UpdateHighSchoolTypeCommand;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.shared.exception.DuplicateHighSchoolTypeNameException;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
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
class UpdateHighSchoolTypeUseCaseImplTest {

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	private UpdateHighSchoolTypeUseCaseImpl useCase;

	private HighSchoolType highSchoolType;

	private UUID highSchoolTypeId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateHighSchoolTypeUseCaseImpl(highSchoolTypeRepository);
		highSchoolType = new HighSchoolType("Conalep");
		highSchoolTypeId = UUID.randomUUID();
		ReflectionTestUtils.setField(highSchoolType, "id", highSchoolTypeId);
	}

	@Test
	void updateHighSchoolType_successfulUpdateLeavesStatusUnchanged() {
		when(highSchoolTypeRepository.findById(highSchoolTypeId)).thenReturn(Optional.of(highSchoolType));
		when(highSchoolTypeRepository.findByName("Cobaem")).thenReturn(Optional.empty());
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase
				.updateHighSchoolType(new UpdateHighSchoolTypeCommand(highSchoolTypeId, "Cobaem"));

		assertThat(result.name()).isEqualTo("Cobaem");
		assertThat(result.status()).isEqualTo(HighSchoolTypeStatus.ACTIVE);
	}

	@Test
	void updateHighSchoolType_allowsKeepingItsOwnCurrentName() {
		// El self-exclusión: la búsqueda por nombre devuelve la propia fila y el
		// filtro por id la descarta, para que guardar sin cambios no sea 409.
		when(highSchoolTypeRepository.findById(highSchoolTypeId)).thenReturn(Optional.of(highSchoolType));
		when(highSchoolTypeRepository.findByName("Conalep")).thenReturn(Optional.of(highSchoolType));
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase
				.updateHighSchoolType(new UpdateHighSchoolTypeCommand(highSchoolTypeId, "Conalep"));

		assertThat(result.name()).isEqualTo("Conalep");
	}

	@Test
	void updateHighSchoolType_rejectsAnotherTypeWithTheSameName() {
		HighSchoolType other = new HighSchoolType("Cobaem");
		ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
		when(highSchoolTypeRepository.findById(highSchoolTypeId)).thenReturn(Optional.of(highSchoolType));
		when(highSchoolTypeRepository.findByName("Cobaem")).thenReturn(Optional.of(other));

		assertThatThrownBy(
				() -> useCase.updateHighSchoolType(new UpdateHighSchoolTypeCommand(highSchoolTypeId, "Cobaem")))
				.isInstanceOf(DuplicateHighSchoolTypeNameException.class);
		verify(highSchoolTypeRepository, never()).save(any());
	}

	@Test
	void updateHighSchoolType_rejectsDuplicateOfAnInactiveType() {
		HighSchoolType inactive = new HighSchoolType("Cobaem");
		inactive.deactivate();
		ReflectionTestUtils.setField(inactive, "id", UUID.randomUUID());
		when(highSchoolTypeRepository.findById(highSchoolTypeId)).thenReturn(Optional.of(highSchoolType));
		when(highSchoolTypeRepository.findByName("Cobaem")).thenReturn(Optional.of(inactive));

		assertThatThrownBy(
				() -> useCase.updateHighSchoolType(new UpdateHighSchoolTypeCommand(highSchoolTypeId, "Cobaem")))
				.isInstanceOf(DuplicateHighSchoolTypeNameException.class);
	}

	@Test
	void updateHighSchoolType_rejectsUnknownId() {
		UUID unknownId = UUID.randomUUID();
		when(highSchoolTypeRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateHighSchoolType(new UpdateHighSchoolTypeCommand(unknownId, "Conalep")))
				.isInstanceOf(HighSchoolTypeNotFoundException.class);
	}
}
