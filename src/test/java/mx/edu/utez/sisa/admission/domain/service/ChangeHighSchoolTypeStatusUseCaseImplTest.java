package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeHighSchoolTypeStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeHighSchoolTypeStatusUseCaseImplTest {

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	private ChangeHighSchoolTypeStatusUseCaseImpl useCase;

	private HighSchoolType highSchoolType;

	private UUID highSchoolTypeId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeHighSchoolTypeStatusUseCaseImpl(highSchoolTypeRepository);
		highSchoolType = new HighSchoolType("Conalep");
		highSchoolTypeId = UUID.randomUUID();
		ReflectionTestUtils.setField(highSchoolType, "id", highSchoolTypeId);
	}

	@Test
	void changeStatus_deactivatesAnActiveType() {
		when(highSchoolTypeRepository.findById(highSchoolTypeId)).thenReturn(Optional.of(highSchoolType));
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), highSchoolTypeId, HighSchoolTypeStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(HighSchoolTypeStatus.INACTIVE);
	}

	@Test
	void changeStatus_reactivatesAnInactiveType() {
		highSchoolType.deactivate();
		when(highSchoolTypeRepository.findById(highSchoolTypeId)).thenReturn(Optional.of(highSchoolType));
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), highSchoolTypeId, HighSchoolTypeStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(HighSchoolTypeStatus.ACTIVE);
	}

	@Test
	void changeStatus_isIdempotentWhenTargetMatchesCurrentStatus() {
		when(highSchoolTypeRepository.findById(highSchoolTypeId)).thenReturn(Optional.of(highSchoolType));
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), highSchoolTypeId, HighSchoolTypeStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(HighSchoolTypeStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsUnknownId() {
		UUID unknownId = UUID.randomUUID();
		when(highSchoolTypeRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, HighSchoolTypeStatus.ACTIVE)))
				.isInstanceOf(HighSchoolTypeNotFoundException.class);
	}
}
