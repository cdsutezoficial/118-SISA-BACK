package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicDivisionUseCase.UpdateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PersonLookupPort;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicDivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DirectorNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionCodeException;
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
class UpdateAcademicDivisionUseCaseImplTest {

	@Mock
	private AcademicDivisionRepository divisionRepository;
	@Mock
	private PersonLookupPort personLookupPort;

	private UpdateAcademicDivisionUseCaseImpl useCase;

	private AcademicDivision divisionA;
	private UUID divisionAId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateAcademicDivisionUseCaseImpl(divisionRepository, personLookupPort);
		divisionA = new AcademicDivision("Diseno", "DSC", "desc", null);
		divisionAId = UUID.randomUUID();
		ReflectionTestUtils.setField(divisionA, "id", divisionAId);
	}

	@Test
	void updateDivision_successfulUpdateLeavesStatusUnchanged() {
		when(divisionRepository.findById(divisionAId)).thenReturn(Optional.of(divisionA));
		when(divisionRepository.findByName("Ingenieria")).thenReturn(Optional.empty());
		when(divisionRepository.findByCode("ING")).thenReturn(Optional.empty());
		when(divisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicDivisionResult result = useCase.updateDivision(
				new UpdateAcademicDivisionCommand(divisionAId, "Ingenieria", "ING", "nueva desc", null));

		assertThat(result.name()).isEqualTo("Ingenieria");
		assertThat(result.code()).isEqualTo("ING");
		assertThat(result.status()).isEqualTo(DivisionStatus.ACTIVE);
	}

	@Test
	void updateDivision_allowsKeepingItsOwnCurrentNameAndCode() {
		when(divisionRepository.findById(divisionAId)).thenReturn(Optional.of(divisionA));
		when(divisionRepository.findByName("Diseno")).thenReturn(Optional.of(divisionA));
		when(divisionRepository.findByCode("DSC")).thenReturn(Optional.of(divisionA));
		when(divisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicDivisionResult result = useCase
				.updateDivision(new UpdateAcademicDivisionCommand(divisionAId, "Diseno", "DSC", "nueva desc", null));

		assertThat(result.name()).isEqualTo("Diseno");
	}

	@Test
	void updateDivision_rejectsCodeConflictWithAnotherDivision() {
		AcademicDivision divisionB = new AcademicDivision("Industrial", "DIN", "desc", null);
		UUID divisionBId = UUID.randomUUID();
		ReflectionTestUtils.setField(divisionB, "id", divisionBId);
		when(divisionRepository.findById(divisionBId)).thenReturn(Optional.of(divisionB));
		when(divisionRepository.findByCode("DSC")).thenReturn(Optional.of(divisionA));

		assertThatThrownBy(() -> useCase
				.updateDivision(new UpdateAcademicDivisionCommand(divisionBId, "Industrial", "DSC", "desc", null)))
				.isInstanceOf(DuplicateDivisionCodeException.class);

		assertThat(divisionB.getCode()).isEqualTo("DIN");
	}

	@Test
	void updateDivision_rejectsNonExistentDirector() {
		UUID directorPersonId = UUID.randomUUID();
		when(divisionRepository.findById(divisionAId)).thenReturn(Optional.of(divisionA));
		when(divisionRepository.findByName("Diseno")).thenReturn(Optional.of(divisionA));
		when(divisionRepository.findByCode("DSC")).thenReturn(Optional.of(divisionA));
		when(personLookupPort.existsById(directorPersonId)).thenReturn(false);

		assertThatThrownBy(() -> useCase.updateDivision(
				new UpdateAcademicDivisionCommand(divisionAId, "Diseno", "DSC", "desc", directorPersonId)))
				.isInstanceOf(DirectorNotFoundException.class);
	}

	@Test
	void updateDivision_rejectsUnknownDivisionId() {
		UUID unknownId = UUID.randomUUID();
		when(divisionRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.updateDivision(new UpdateAcademicDivisionCommand(unknownId, "Diseno", "DSC", "desc", null)))
				.isInstanceOf(AcademicDivisionNotFoundException.class);
	}
}
