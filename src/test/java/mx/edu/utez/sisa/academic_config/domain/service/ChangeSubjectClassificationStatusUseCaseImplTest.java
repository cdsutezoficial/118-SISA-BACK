package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeSubjectClassificationStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
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
class ChangeSubjectClassificationStatusUseCaseImplTest {

	@Mock
	private SubjectClassificationRepository classificationRepository;

	private ChangeSubjectClassificationStatusUseCaseImpl useCase;

	private SubjectClassification classification;
	private UUID classificationId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeSubjectClassificationStatusUseCaseImpl(classificationRepository);
		classification = new SubjectClassification("Integradora", "INT");
		classificationId = UUID.randomUUID();
		ReflectionTestUtils.setField(classification, "id", classificationId);
	}

	@Test
	void changeStatus_deactivatesAnActiveClassification() {
		when(classificationRepository.findById(classificationId)).thenReturn(Optional.of(classification));
		when(classificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ClassificationResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), classificationId, ClassificationStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(ClassificationStatus.INACTIVE);
	}

	@Test
	void changeStatus_reactivatesAnInactiveClassification() {
		classification.deactivate();
		when(classificationRepository.findById(classificationId)).thenReturn(Optional.of(classification));
		when(classificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ClassificationResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), classificationId, ClassificationStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(ClassificationStatus.ACTIVE);
	}

	@Test
	void changeStatus_isIdempotentWhenTargetMatchesCurrentStatus() {
		when(classificationRepository.findById(classificationId)).thenReturn(Optional.of(classification));
		when(classificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ClassificationResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), classificationId, ClassificationStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(ClassificationStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsUnknownClassificationId() {
		UUID unknownId = UUID.randomUUID();
		when(classificationRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, ClassificationStatus.ACTIVE)))
				.isInstanceOf(ClassificationNotFoundException.class);
	}
}
