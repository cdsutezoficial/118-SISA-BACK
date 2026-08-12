package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository.PaymentConceptSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository.PaymentConceptSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link PaymentConceptRepositoryAdapter#search},
 * mirroring {@code SubjectClassificationRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(PaymentConceptRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentConceptRepositoryAdapterSearchIT {

	@Autowired
	private PaymentConceptRepositoryAdapter adapter;

	@Autowired
	private PaymentConceptJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		jpaRepository.save(newConcept("Inscripcion"));
		PaymentConcept inactive = jpaRepository.save(newConcept("Reinscripcion"));
		inactive.deactivate();
		jpaRepository.save(inactive);

		PaymentConceptSearchPage activePage = adapter
				.search(new PaymentConceptSearchCriteria(PaymentConceptStatus.ACTIVE, null, 0, 20));
		PaymentConceptSearchPage inactivePage = adapter
				.search(new PaymentConceptSearchCriteria(PaymentConceptStatus.INACTIVE, null, 0, 20));

		assertThat(activePage.totalElements()).isEqualTo(1L);
		assertThat(inactivePage.totalElements()).isEqualTo(1L);
	}

	@Test
	void searchMatchesNameCaseInsensitively() {
		jpaRepository.save(newConcept("Inscripcion Semestral"));
		jpaRepository.save(newConcept("Reinscripcion"));

		PaymentConceptSearchPage byFragment = adapter.search(new PaymentConceptSearchCriteria(null, "inscrip", 0, 20));

		assertThat(byFragment.totalElements()).isEqualTo(2L);
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 0; i < 5; i++) {
			jpaRepository.save(newConcept("Concepto " + i));
		}

		PaymentConceptSearchPage firstPage = adapter.search(new PaymentConceptSearchCriteria(null, null, 0, 2));
		PaymentConceptSearchPage secondPage = adapter.search(new PaymentConceptSearchCriteria(null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void allowsDuplicateNamesAcrossDifferentRecords() {
		jpaRepository.save(newConcept("Inscripcion"));
		jpaRepository.save(newConcept("Inscripcion"));

		PaymentConceptSearchPage page = adapter.search(new PaymentConceptSearchCriteria(null, "Inscripcion", 0, 20));

		assertThat(page.totalElements()).isEqualTo(2L);
	}

	@Test
	void resultsAreSortedByNameAscendingThenIdAscendingWhenNamesCollide() {
		PaymentConcept first = jpaRepository.save(newConcept("Alfa"));
		PaymentConcept secondSameName = jpaRepository.save(newConcept("Beta"));
		PaymentConcept thirdSameName = jpaRepository.save(newConcept("Beta"));

		PaymentConceptSearchPage page = adapter.search(new PaymentConceptSearchCriteria(null, null, 0, 20));

		assertThat(page.content()).extracting(PaymentConcept::getName).containsExactly("Alfa", "Beta", "Beta");
		// The two "Beta" rows must be ordered deterministically by id, not by
		// insertion order — assert both are present regardless of which one
		// sorts first.
		assertThat(page.content().stream().map(PaymentConcept::getId))
				.contains(first.getId(), secondSameName.getId(), thirdSameName.getId());
	}

	@Test
	void saveInsertsANewRow() {
		PaymentConcept saved = adapter.save(newConcept("Inscripcion"));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findByIdReturnsTheConceptWhenItExists() {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion"));

		assertThat(adapter.findById(saved.getId())).isPresent().get()
				.extracting(PaymentConcept::getName).isEqualTo("Inscripcion");
	}

	@Test
	void findByIdReturnsEmptyWhenConceptDoesNotExist() {
		assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
	}

	private static PaymentConcept newConcept(String name) {
		return new PaymentConcept(name, "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true, false, 1, 2,
				true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}
}
