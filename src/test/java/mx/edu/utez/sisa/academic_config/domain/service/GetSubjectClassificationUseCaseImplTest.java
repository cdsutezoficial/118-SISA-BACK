package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSubjectClassificationUseCaseImplTest {

	@Mock
	private SubjectClassificationRepository classificationRepository;

	private GetSubjectClassificationUseCaseImpl useCase;

	private SubjectClassification classification;
	private UUID classificationId;

	@BeforeEach
	void setUp() {
		useCase = new GetSubjectClassificationUseCaseImpl(classificationRepository);
		classification = new SubjectClassification("Integradora", "INT");
		classificationId = UUID.randomUUID();
		ReflectionTestUtils.setField(classification, "id", classificationId);
	}

	@Test
	void getById_returnsTheClassificationWhenItExists() {
		when(classificationRepository.findById(classificationId)).thenReturn(Optional.of(classification));

		ClassificationResult result = useCase.getById(classificationId);

		assertThat(result.id()).isEqualTo(classificationId);
		assertThat(result.name()).isEqualTo("Integradora");
		assertThat(result.code()).isEqualTo("INT");
	}

	@Test
	void getById_rejectsUnknownClassificationId() {
		UUID unknownId = UUID.randomUUID();
		when(classificationRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(ClassificationNotFoundException.class);
	}
}
