package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository.HighSchoolTypeSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository.HighSchoolTypeSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link HighSchoolTypeRepositoryAdapter#search},
 * mirroring {@code OutreachChannelRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(HighSchoolTypeRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class HighSchoolTypeRepositoryAdapterSearchIT {

	@Autowired
	private HighSchoolTypeRepositoryAdapter adapter;

	@Autowired
	private HighSchoolTypeJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		jpaRepository.save(new HighSchoolType("Conalep"));
		jpaRepository.save(new HighSchoolType("Cobaem"));

		HighSchoolTypeSearchPage activePage = adapter
				.search(new HighSchoolTypeSearchCriteria(HighSchoolTypeStatus.ACTIVE, null, 0, 20));
		HighSchoolTypeSearchPage inactivePage = adapter
				.search(new HighSchoolTypeSearchCriteria(HighSchoolTypeStatus.INACTIVE, null, 0, 20));

		assertThat(activePage.totalElements()).isEqualTo(2L);
		assertThat(inactivePage.totalElements()).isZero();
	}

	@Test
	void searchMatchesNameCaseInsensitively() {
		jpaRepository.save(new HighSchoolType("Conalep"));
		jpaRepository.save(new HighSchoolType("Cobaem"));

		HighSchoolTypeSearchPage byFragment = adapter.search(new HighSchoolTypeSearchCriteria(null, "cona", 0, 20));

		assertThat(byFragment.totalElements()).isEqualTo(1L);
		assertThat(byFragment.content().get(0).getName()).isEqualTo("Conalep");
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 0; i < 5; i++) {
			jpaRepository.save(new HighSchoolType("Tipo " + i));
		}

		HighSchoolTypeSearchPage firstPage = adapter.search(new HighSchoolTypeSearchCriteria(null, null, 0, 2));
		HighSchoolTypeSearchPage secondPage = adapter.search(new HighSchoolTypeSearchCriteria(null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void allowsDuplicateNames() {
		jpaRepository.save(new HighSchoolType("Conalep"));
		jpaRepository.save(new HighSchoolType("Conalep"));

		HighSchoolTypeSearchPage page = adapter.search(new HighSchoolTypeSearchCriteria(null, "Conalep", 0, 20));

		assertThat(page.totalElements()).isEqualTo(2L);
	}

	@Test
	void resultsAreSortedDeterministicallyAcrossRepeatedQueriesWhenNamesTie() {
		jpaRepository.save(new HighSchoolType("Conalep"));
		jpaRepository.save(new HighSchoolType("Conalep"));

		HighSchoolTypeSearchPage firstCall = adapter.search(new HighSchoolTypeSearchCriteria(null, null, 0, 20));
		HighSchoolTypeSearchPage secondCall = adapter.search(new HighSchoolTypeSearchCriteria(null, null, 0, 20));

		assertThat(firstCall.content()).extracting(HighSchoolType::getId)
				.containsExactlyElementsOf(secondCall.content().stream().map(HighSchoolType::getId).toList());
	}

	@Test
	void saveInsertsANewRow() {
		HighSchoolType saved = adapter.save(new HighSchoolType("Conalep"));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findByIdReturnsTheTypeWhenItExists() {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep"));

		assertThat(adapter.findById(saved.getId())).isPresent().get().extracting(HighSchoolType::getName)
				.isEqualTo("Conalep");
	}

	@Test
	void findByIdReturnsEmptyWhenTypeDoesNotExist() {
		assertThat(adapter.findById(java.util.UUID.randomUUID())).isEmpty();
	}
}
