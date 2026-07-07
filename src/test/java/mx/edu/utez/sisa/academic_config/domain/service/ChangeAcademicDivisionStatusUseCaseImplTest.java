package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicDivisionStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicDivisionNotFoundException;
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
class ChangeAcademicDivisionStatusUseCaseImplTest {

	@Mock
	private AcademicDivisionRepository divisionRepository;

	private ChangeAcademicDivisionStatusUseCaseImpl useCase;

	private AcademicDivision division;
	private UUID divisionId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeAcademicDivisionStatusUseCaseImpl(divisionRepository);
		division = new AcademicDivision("Diseno", "DSC", "desc", null);
		divisionId = UUID.randomUUID();
		ReflectionTestUtils.setField(division, "id", divisionId);
	}

	@Test
	void changeStatus_deactivatesAnActiveDivision() {
		when(divisionRepository.findById(divisionId)).thenReturn(Optional.of(division));
		when(divisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicDivisionResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), divisionId, DivisionStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(DivisionStatus.INACTIVE);
	}

	@Test
	void changeStatus_reactivatesAnInactiveDivision() {
		division.deactivate();
		when(divisionRepository.findById(divisionId)).thenReturn(Optional.of(division));
		when(divisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicDivisionResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), divisionId, DivisionStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(DivisionStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsUnknownDivisionId() {
		UUID unknownId = UUID.randomUUID();
		when(divisionRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, DivisionStatus.ACTIVE)))
				.isInstanceOf(AcademicDivisionNotFoundException.class);
	}
}
