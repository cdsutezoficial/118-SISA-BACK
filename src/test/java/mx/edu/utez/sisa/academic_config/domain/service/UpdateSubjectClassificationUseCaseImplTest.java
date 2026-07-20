package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectClassificationUseCase.UpdateClassificationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateClassificationCodeException;
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
class UpdateSubjectClassificationUseCaseImplTest {

	@Mock
	private SubjectClassificationRepository classificationRepository;

	private UpdateSubjectClassificationUseCaseImpl useCase;

	private SubjectClassification classificationA;
	private UUID classificationAId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateSubjectClassificationUseCaseImpl(classificationRepository);
		classificationA = new SubjectClassification("Integradora", "INT");
		classificationAId = UUID.randomUUID();
		ReflectionTestUtils.setField(classificationA, "id", classificationAId);
	}

	@Test
	void updateClassification_successfulUpdateLeavesStatusUnchanged() {
		when(classificationRepository.findById(classificationAId)).thenReturn(Optional.of(classificationA));
		when(classificationRepository.findByCode("REG")).thenReturn(Optional.empty());
		when(classificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ClassificationResult result = useCase
				.updateClassification(new UpdateClassificationCommand(classificationAId, "Regular", "REG"));

		assertThat(result.name()).isEqualTo("Regular");
		assertThat(result.code()).isEqualTo("REG");
		assertThat(result.status()).isEqualTo(ClassificationStatus.ACTIVE);
	}

	@Test
	void updateClassification_allowsKeepingItsOwnCurrentCode() {
		when(classificationRepository.findById(classificationAId)).thenReturn(Optional.of(classificationA));
		when(classificationRepository.findByCode("INT")).thenReturn(Optional.of(classificationA));
		when(classificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ClassificationResult result = useCase
				.updateClassification(new UpdateClassificationCommand(classificationAId, "Integradora renombrada", "INT"));

		assertThat(result.name()).isEqualTo("Integradora renombrada");
		assertThat(result.code()).isEqualTo("INT");
	}

	@Test
	void updateClassification_rejectsCodeConflictWithAnotherClassification() {
		SubjectClassification classificationB = new SubjectClassification("Regular", "REG");
		UUID classificationBId = UUID.randomUUID();
		ReflectionTestUtils.setField(classificationB, "id", classificationBId);
		when(classificationRepository.findById(classificationBId)).thenReturn(Optional.of(classificationB));
		when(classificationRepository.findByCode("INT")).thenReturn(Optional.of(classificationA));

		assertThatThrownBy(() -> useCase
				.updateClassification(new UpdateClassificationCommand(classificationBId, "Regular", "INT")))
				.isInstanceOf(DuplicateClassificationCodeException.class);

		assertThat(classificationB.getCode()).isEqualTo("REG");
	}

	@Test
	void updateClassification_rejectsUnknownClassificationId() {
		UUID unknownId = UUID.randomUUID();
		when(classificationRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> useCase.updateClassification(new UpdateClassificationCommand(unknownId, "Integradora", "INT")))
				.isInstanceOf(ClassificationNotFoundException.class);
	}
}
