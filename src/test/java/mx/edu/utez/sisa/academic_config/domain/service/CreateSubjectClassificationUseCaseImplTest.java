package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.CreateClassificationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateClassificationCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateSubjectClassificationUseCaseImplTest {

	@Mock
	private SubjectClassificationRepository classificationRepository;

	private CreateSubjectClassificationUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreateSubjectClassificationUseCaseImpl(classificationRepository);
	}

	@Test
	void createClassification_successfulCreation() {
		when(classificationRepository.findByCode("INT")).thenReturn(Optional.empty());
		when(classificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ClassificationResult result = useCase.createClassification(new CreateClassificationCommand("Integradora", "INT"));

		assertThat(result.status()).isEqualTo(ClassificationStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("Integradora");
		assertThat(result.code()).isEqualTo("INT");
	}

	@Test
	void createClassification_rejectsDuplicateCode() {
		SubjectClassification existing = new SubjectClassification("Otra", "INT");
		when(classificationRepository.findByCode("INT")).thenReturn(Optional.of(existing));

		assertThatThrownBy(
				() -> useCase.createClassification(new CreateClassificationCommand("Integradora", "INT")))
				.isInstanceOf(DuplicateClassificationCodeException.class);

		verify(classificationRepository, never()).save(any());
	}

	@Test
	void createClassification_allowsDuplicateName() {
		// name is deliberately NOT unique for this aggregate (unlike
		// AcademicDivision.name) — see SubjectClassification's javadoc and the
		// domain doc (02-config-academica.md lines 15-24). Only code uniqueness
		// is validated, so two different codes with the same name must both
		// succeed.
		when(classificationRepository.findByCode("INT")).thenReturn(Optional.empty());
		when(classificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ClassificationResult result = useCase.createClassification(new CreateClassificationCommand("Integradora", "INT"));

		assertThat(result.name()).isEqualTo("Integradora");
	}
}
