package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository.ClassificationSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository.ClassificationSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link SubjectClassificationRepositoryAdapter#search},
 * mirroring {@code AcademicDivisionRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(SubjectClassificationRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SubjectClassificationRepositoryAdapterSearchIT {

	@Autowired
	private SubjectClassificationRepositoryAdapter adapter;

	@Autowired
	private SubjectClassificationJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		// No ChangeStatus use case exists yet (Phase 1 — YAGNI), so every
		// persisted row is ACTIVE by construction. This exercises the filter
		// end-to-end: ACTIVE matches both seeded rows, INACTIVE matches none.
		jpaRepository.save(newClassification("Integradora", "INT"));
		jpaRepository.save(newClassification("Regular", "REG"));

		ClassificationSearchPage activePage = adapter
				.search(new ClassificationSearchCriteria(ClassificationStatus.ACTIVE, null, 0, 20));
		ClassificationSearchPage inactivePage = adapter
				.search(new ClassificationSearchCriteria(ClassificationStatus.INACTIVE, null, 0, 20));

		assertThat(activePage.totalElements()).isEqualTo(2L);
		assertThat(inactivePage.totalElements()).isZero();
	}

	@Test
	void searchMatchesNameOrCodeCaseInsensitively() {
		jpaRepository.save(newClassification("Integradora", "INT"));
		jpaRepository.save(newClassification("Regular", "REG"));

		ClassificationSearchPage byNameFragment = adapter.search(new ClassificationSearchCriteria(null, "integ", 0, 20));
		ClassificationSearchPage byCodeFragment = adapter.search(new ClassificationSearchCriteria(null, "reg", 0, 20));

		assertThat(byNameFragment.totalElements()).isEqualTo(1L);
		assertThat(byNameFragment.content().get(0).getCode()).isEqualTo("INT");
		assertThat(byCodeFragment.totalElements()).isEqualTo(1L);
		assertThat(byCodeFragment.content().get(0).getCode()).isEqualTo("REG");
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 0; i < 5; i++) {
			jpaRepository.save(newClassification("Clasificacion " + i, "CLS" + i));
		}

		ClassificationSearchPage firstPage = adapter.search(new ClassificationSearchCriteria(null, null, 0, 2));
		ClassificationSearchPage secondPage = adapter.search(new ClassificationSearchCriteria(null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void resultsAreSortedByNameAscendingThenCodeAscending() {
		jpaRepository.save(newClassification("Regular", "REG"));
		jpaRepository.save(newClassification("Integradora", "INT"));
		jpaRepository.save(newClassification("Integradora", "INT2"));

		ClassificationSearchPage page = adapter.search(new ClassificationSearchCriteria(null, null, 0, 20));

		assertThat(page.content()).extracting(SubjectClassification::getCode)
				.containsExactly("INT", "INT2", "REG");
	}

	@Test
	void allowsDuplicateNamesAcrossDifferentCodes() {
		jpaRepository.save(newClassification("Integradora", "INT"));
		jpaRepository.save(newClassification("Integradora", "INT2"));

		ClassificationSearchPage page = adapter.search(new ClassificationSearchCriteria(null, "Integradora", 0, 20));

		assertThat(page.totalElements()).isEqualTo(2L);
	}

	private static SubjectClassification newClassification(String name, String code) {
		return new SubjectClassification(name, code);
	}
}
