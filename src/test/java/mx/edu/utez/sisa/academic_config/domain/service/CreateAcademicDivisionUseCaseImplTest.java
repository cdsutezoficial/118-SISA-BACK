package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.CreateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PersonLookupPort;
import mx.edu.utez.sisa.academic_config.shared.exception.DirectorNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionNameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAcademicDivisionUseCaseImplTest {

	@Mock
	private AcademicDivisionRepository divisionRepository;
	@Mock
	private PersonLookupPort personLookupPort;

	private CreateAcademicDivisionUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreateAcademicDivisionUseCaseImpl(divisionRepository, personLookupPort);
	}

	@Test
	void createDivision_successfulCreationWithADirector() {
		UUID directorPersonId = UUID.randomUUID();
		when(divisionRepository.findByName("Diseno")).thenReturn(Optional.empty());
		when(divisionRepository.findByCode("DSC")).thenReturn(Optional.empty());
		when(personLookupPort.existsById(directorPersonId)).thenReturn(true);
		when(divisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicDivisionResult result = useCase.createDivision(
				new CreateAcademicDivisionCommand("Diseno", "DSC", "Division de diseno", directorPersonId));

		assertThat(result.status()).isEqualTo(DivisionStatus.ACTIVE);
		assertThat(result.directorPersonId()).isEqualTo(directorPersonId);
		assertThat(result.name()).isEqualTo("Diseno");
		assertThat(result.code()).isEqualTo("DSC");
	}

	@Test
	void createDivision_successfulCreationWithoutADirector() {
		when(divisionRepository.findByName("Diseno")).thenReturn(Optional.empty());
		when(divisionRepository.findByCode("DSC")).thenReturn(Optional.empty());
		when(divisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicDivisionResult result = useCase
				.createDivision(new CreateAcademicDivisionCommand("Diseno", "DSC", "Division de diseno", null));

		assertThat(result.status()).isEqualTo(DivisionStatus.ACTIVE);
		assertThat(result.directorPersonId()).isNull();
	}

	@Test
	void createDivision_rejectsDuplicateCode() {
		AcademicDivision existing = new AcademicDivision("Otra", "DSC", "desc", null);
		when(divisionRepository.findByName("Diseno")).thenReturn(Optional.empty());
		when(divisionRepository.findByCode("DSC")).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> useCase
				.createDivision(new CreateAcademicDivisionCommand("Diseno", "DSC", "desc", null)))
				.isInstanceOf(DuplicateDivisionCodeException.class);

		verify(divisionRepository, never()).save(any());
	}

	@Test
	void createDivision_rejectsDuplicateName() {
		AcademicDivision existing = new AcademicDivision("Diseno", "OTR", "desc", null);
		when(divisionRepository.findByName("Diseno")).thenReturn(Optional.of(existing));

		assertThatThrownBy(
				() -> useCase.createDivision(new CreateAcademicDivisionCommand("Diseno", "DSC", "desc", null)))
				.isInstanceOf(DuplicateDivisionNameException.class);

		verify(divisionRepository, never()).save(any());
	}

	@Test
	void createDivision_rejectsNonExistentDirector() {
		UUID directorPersonId = UUID.randomUUID();
		when(divisionRepository.findByName("Diseno")).thenReturn(Optional.empty());
		when(divisionRepository.findByCode("DSC")).thenReturn(Optional.empty());
		when(personLookupPort.existsById(directorPersonId)).thenReturn(false);

		assertThatThrownBy(() -> useCase.createDivision(
				new CreateAcademicDivisionCommand("Diseno", "DSC", "desc", directorPersonId)))
				.isInstanceOf(DirectorNotFoundException.class);

		verify(divisionRepository, never()).save(any());
	}
}
