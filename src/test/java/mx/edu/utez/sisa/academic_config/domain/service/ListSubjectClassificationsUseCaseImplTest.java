package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ClassificationSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository.ClassificationSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository.ClassificationSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSubjectClassificationsUseCaseImplTest {

	@Mock
	private SubjectClassificationRepository classificationRepository;

	private ListSubjectClassificationsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListSubjectClassificationsUseCaseImpl(classificationRepository);
	}

	@Test
	void listClassifications_defaultPaginationUsesDefaultSize() {
		List<SubjectClassification> content = List.of(new SubjectClassification("Integradora", "INT"),
				new SubjectClassification("Regular", "REG"));
		when(classificationRepository.search(any())).thenReturn(new ClassificationSearchPage(content, 22, 2));

		ListSubjectClassificationsResult result = useCase
				.listClassifications(new ListSubjectClassificationsQuery(null, null, 0, 0));

		ArgumentCaptor<ClassificationSearchCriteria> captor = ArgumentCaptor.forClass(ClassificationSearchCriteria.class);
		verify(classificationRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListSubjectClassificationsQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listClassifications_mapsEntitiesToSummaries() {
		List<SubjectClassification> content = List.of(new SubjectClassification("Integradora", "INT"),
				new SubjectClassification("Regular", "REG"));
		when(classificationRepository.search(any())).thenReturn(new ClassificationSearchPage(content, 2, 1));

		ListSubjectClassificationsResult result = useCase
				.listClassifications(new ListSubjectClassificationsQuery(null, null, 0, 20));

		assertThat(result.items()).hasSize(2).extracting(ClassificationSummary::name)
				.containsExactlyInAnyOrder("Integradora", "Regular");
	}

	@Test
	void listClassifications_oversizedPageIsCappedAtMax() {
		when(classificationRepository.search(any())).thenReturn(new ClassificationSearchPage(List.of(), 0, 0));

		useCase.listClassifications(new ListSubjectClassificationsQuery(null, null, 0, 500));

		ArgumentCaptor<ClassificationSearchCriteria> captor = ArgumentCaptor.forClass(ClassificationSearchCriteria.class);
		verify(classificationRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListSubjectClassificationsQuery.MAX_PAGE_SIZE);
	}

	@Test
	void listClassifications_negativePageIsNormalizedToZero() {
		when(classificationRepository.search(any())).thenReturn(new ClassificationSearchPage(List.of(), 0, 0));

		useCase.listClassifications(new ListSubjectClassificationsQuery(null, null, -5, 20));

		ArgumentCaptor<ClassificationSearchCriteria> captor = ArgumentCaptor.forClass(ClassificationSearchCriteria.class);
		verify(classificationRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isZero();
	}
}
