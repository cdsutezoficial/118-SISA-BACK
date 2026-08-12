package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHighSchoolTypeUseCaseImplTest {

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	private GetHighSchoolTypeUseCaseImpl useCase;

	private HighSchoolType highSchoolType;

	private UUID highSchoolTypeId;

	@BeforeEach
	void setUp() {
		useCase = new GetHighSchoolTypeUseCaseImpl(highSchoolTypeRepository);
		highSchoolType = new HighSchoolType("Conalep");
		highSchoolTypeId = UUID.randomUUID();
		ReflectionTestUtils.setField(highSchoolType, "id", highSchoolTypeId);
	}

	@Test
	void getById_returnsTheTypeWhenItExists() {
		when(highSchoolTypeRepository.findById(highSchoolTypeId)).thenReturn(Optional.of(highSchoolType));

		HighSchoolTypeResult result = useCase.getById(highSchoolTypeId);

		assertThat(result.id()).isEqualTo(highSchoolTypeId);
		assertThat(result.name()).isEqualTo("Conalep");
	}

	@Test
	void getById_rejectsUnknownId() {
		UUID unknownId = UUID.randomUUID();
		when(highSchoolTypeRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(HighSchoolTypeNotFoundException.class);
	}
}
