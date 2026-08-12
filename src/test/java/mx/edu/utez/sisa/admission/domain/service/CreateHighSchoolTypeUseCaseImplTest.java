package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.CreateHighSchoolTypeCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult result = useCase.createHighSchoolType(new CreateHighSchoolTypeCommand("Conalep"));

		assertThat(result.status()).isEqualTo(HighSchoolTypeStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("Conalep");
	}

	@Test
	void createHighSchoolType_allowsDuplicateName() {
		// name is deliberately NOT unique for this aggregate — see
		// HighSchoolType's javadoc. There is no uniqueness check at all, so
		// two types with the same name must both succeed.
		when(highSchoolTypeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		HighSchoolTypeResult first = useCase.createHighSchoolType(new CreateHighSchoolTypeCommand("Conalep"));
		HighSchoolTypeResult second = useCase.createHighSchoolType(new CreateHighSchoolTypeCommand("Conalep"));

		assertThat(first.name()).isEqualTo("Conalep");
		assertThat(second.name()).isEqualTo("Conalep");
	}
}
