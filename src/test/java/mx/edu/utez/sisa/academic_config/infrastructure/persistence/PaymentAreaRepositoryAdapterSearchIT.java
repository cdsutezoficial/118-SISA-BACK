package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository.PaymentAreaSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository.PaymentAreaSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link PaymentAreaRepositoryAdapter}, mirroring
 * {@code AcademicDivisionRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(PaymentAreaRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentAreaRepositoryAdapterSearchIT {

	@Autowired
	private PaymentAreaRepositoryAdapter adapter;

	@Autowired
	private PaymentAreaJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		jpaRepository.save(newArea("Colegiaturas", "COL"));
		PaymentArea inactive = jpaRepository.save(newArea("Inscripcion", "INS"));
		inactive.deactivate();
		jpaRepository.save(inactive);

		PaymentAreaSearchPage activePage = adapter
				.search(new PaymentAreaSearchCriteria(PaymentAreaStatus.ACTIVE, null, 0, 20));
		PaymentAreaSearchPage inactivePage = adapter
				.search(new PaymentAreaSearchCriteria(PaymentAreaStatus.INACTIVE, null, 0, 20));

		assertThat(activePage.totalElements()).isEqualTo(1L);
		assertThat(inactivePage.totalElements()).isEqualTo(1L);
	}

	@Test
	void searchMatchesNameCaseInsensitively() {
		jpaRepository.save(newArea("Colegiaturas", "COL"));
		jpaRepository.save(newArea("Inscripcion", "INS"));

		PaymentAreaSearchPage byFragment = adapter.search(new PaymentAreaSearchCriteria(null, "coleg", 0, 20));

		assertThat(byFragment.totalElements()).isEqualTo(1L);
	}

	@Test
	void searchMatchesCodeCaseInsensitively() {
		jpaRepository.save(newArea("Colegiaturas", "COL"));
		jpaRepository.save(newArea("Inscripcion", "INS"));

		PaymentAreaSearchPage byFragment = adapter.search(new PaymentAreaSearchCriteria(null, "ins", 0, 20));

		assertThat(byFragment.totalElements()).isEqualTo(1L);
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 0; i < 5; i++) {
			jpaRepository.save(newArea("Area " + i, "A" + i));
		}

		PaymentAreaSearchPage firstPage = adapter.search(new PaymentAreaSearchCriteria(null, null, 0, 2));
		PaymentAreaSearchPage secondPage = adapter.search(new PaymentAreaSearchCriteria(null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void resultsAreSortedByNameAscending() {
		jpaRepository.save(newArea("Zeta", "Z"));
		jpaRepository.save(newArea("Alfa", "A"));
		jpaRepository.save(newArea("Beta", "B"));

		PaymentAreaSearchPage page = adapter.search(new PaymentAreaSearchCriteria(null, null, 0, 20));

		assertThat(page.content()).extracting(PaymentArea::getName).containsExactly("Alfa", "Beta", "Zeta");
	}

	@Test
	void findByCodeIsCaseInsensitive() {
		jpaRepository.save(newArea("Colegiaturas", "COL"));

		assertThat(adapter.findByCode("col")).isPresent().get().extracting(PaymentArea::getName)
				.isEqualTo("Colegiaturas");
	}

	@Test
	void findByNameIsCaseInsensitive() {
		jpaRepository.save(newArea("Colegiaturas", "COL"));

		assertThat(adapter.findByName("colegiaturas")).isPresent().get().extracting(PaymentArea::getCode)
				.isEqualTo("COL");
	}

	@Test
	void findByCodeReturnsEmptyWhenMissing() {
		assertThat(adapter.findByCode("NOPE")).isEmpty();
	}

	@Test
	void findByNameReturnsEmptyWhenMissing() {
		assertThat(adapter.findByName("Nope")).isEmpty();
	}

	@Test
	void saveInsertsANewRow() {
		PaymentArea saved = adapter.save(newArea("Colegiaturas", "COL"));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findByIdReturnsTheAreaWhenItExists() {
		PaymentArea saved = jpaRepository.save(newArea("Colegiaturas", "COL"));

		assertThat(adapter.findById(saved.getId())).isPresent().get().extracting(PaymentArea::getName)
				.isEqualTo("Colegiaturas");
	}

	@Test
	void findByIdReturnsEmptyWhenAreaDoesNotExist() {
		assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
	}

	private static PaymentArea newArea(String name, String code) {
		return new PaymentArea(name, code, "Descripcion");
	}
}
