package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository.DivisionSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository.DivisionSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link AcademicDivisionRepositoryAdapter#search}
 * and the case-insensitive {@code findByCode}/{@code findByName} contract
 * (design.md — Testing Strategy: "search filter/pagination/sort,
 * case-insensitive uniqueness"), mirroring
 * {@code identity.UserRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(AcademicDivisionRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AcademicDivisionRepositoryAdapterSearchIT {

	@Autowired
	private AcademicDivisionRepositoryAdapter adapter;

	@Autowired
	private AcademicDivisionJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		AcademicDivision active = jpaRepository.save(newDivision("Ingeniería en Software", "ISW"));
		AcademicDivision inactive = jpaRepository.save(newDivision("Diseño Gráfico", "DGR"));
		inactive.deactivate();
		jpaRepository.save(inactive);

		DivisionSearchPage page = adapter.search(new DivisionSearchCriteria(DivisionStatus.INACTIVE, null, 0, 20));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.content().get(0).getCode()).isEqualTo("DGR");
		assertThat(active).isNotNull();
	}

	@Test
	void searchMatchesNameOrCodeCaseInsensitively() {
		jpaRepository.save(newDivision("Ingeniería en Software", "ISW"));
		jpaRepository.save(newDivision("Diseño Gráfico", "DGR"));

		DivisionSearchPage byNameFragment = adapter.search(new DivisionSearchCriteria(null, "software", 0, 20));
		DivisionSearchPage byCodeFragment = adapter.search(new DivisionSearchCriteria(null, "dgr", 0, 20));

		assertThat(byNameFragment.totalElements()).isEqualTo(1L);
		assertThat(byNameFragment.content().get(0).getCode()).isEqualTo("ISW");
		assertThat(byCodeFragment.totalElements()).isEqualTo(1L);
		assertThat(byCodeFragment.content().get(0).getCode()).isEqualTo("DGR");
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 0; i < 5; i++) {
			jpaRepository.save(newDivision("Division " + i, "DIV" + i));
		}

		DivisionSearchPage firstPage = adapter.search(new DivisionSearchCriteria(null, null, 0, 2));
		DivisionSearchPage secondPage = adapter.search(new DivisionSearchCriteria(null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void resultsAreSortedByNameAscending() {
		jpaRepository.save(newDivision("Zootecnia", "ZOO"));
		jpaRepository.save(newDivision("Agronomía", "AGR"));
		jpaRepository.save(newDivision("Medicina", "MED"));

		DivisionSearchPage page = adapter.search(new DivisionSearchCriteria(null, null, 0, 20));

		assertThat(page.content()).extracting(AcademicDivision::getName)
				.containsExactly("Agronomía", "Medicina", "Zootecnia");
	}

	@Test
	void findByCodeIsCaseInsensitive() {
		jpaRepository.save(newDivision("Ingeniería en Software", "ISW"));

		assertThat(adapter.findByCode("isw")).isPresent();
		assertThat(adapter.findByCode("ISW")).isPresent();
		assertThat(adapter.findByCode("unknown")).isEmpty();
	}

	@Test
	void findByNameIsCaseInsensitive() {
		jpaRepository.save(newDivision("Ingeniería en Software", "ISW"));

		assertThat(adapter.findByName("ingeniería en software")).isPresent();
		assertThat(adapter.findByName("INGENIERÍA EN SOFTWARE")).isPresent();
		assertThat(adapter.findByName("unknown")).isEmpty();
	}

	private static AcademicDivision newDivision(String name, String code) {
		return new AcademicDivision(name, code, "Description for " + name, (UUID) null);
	}
}
