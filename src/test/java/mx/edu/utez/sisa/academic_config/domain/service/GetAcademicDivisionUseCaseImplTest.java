package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAcademicDivisionUseCaseImplTest {

	@Mock
	private AcademicDivisionRepository divisionRepository;

	private GetAcademicDivisionUseCaseImpl useCase;

	private AcademicDivision division;
	private UUID divisionId;

	@BeforeEach
	void setUp() {
		useCase = new GetAcademicDivisionUseCaseImpl(divisionRepository);
		division = new AcademicDivision("Diseno", "DSC", "desc", null);
		divisionId = UUID.randomUUID();
		ReflectionTestUtils.setField(division, "id", divisionId);
	}

	@Test
	void getById_returnsTheDivisionWhenItExists() {
		when(divisionRepository.findById(divisionId)).thenReturn(Optional.of(division));

		AcademicDivisionResult result = useCase.getById(divisionId);

		assertThat(result.id()).isEqualTo(divisionId);
		assertThat(result.name()).isEqualTo("Diseno");
		assertThat(result.code()).isEqualTo("DSC");
	}

	@Test
	void getById_rejectsUnknownDivisionId() {
		UUID unknownId = UUID.randomUUID();
		when(divisionRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(AcademicDivisionNotFoundException.class);
	}
}
