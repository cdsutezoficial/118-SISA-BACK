package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository.PeriodSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository.PeriodSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Real-DB (H2) coverage for {@link AcademicPeriodRepositoryAdapter#search},
 * mirroring {@code SubjectClassificationRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(AcademicPeriodRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AcademicPeriodRepositoryAdapterSearchIT {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Autowired
	private AcademicPeriodRepositoryAdapter adapter;

	@Autowired
	private AcademicPeriodJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		jpaRepository.save(newPeriod("Enero-Abril 2026", 2026, 1));
		jpaRepository.save(newPeriod("Mayo-Agosto 2026", 2026, 2));

		PeriodSearchPage configurationPage = adapter
				.search(new PeriodSearchCriteria(PeriodStatus.CONFIGURATION, null, 0, 20));
		PeriodSearchPage closedPage = adapter.search(new PeriodSearchCriteria(PeriodStatus.CLOSED, null, 0, 20));

		assertThat(configurationPage.totalElements()).isEqualTo(2L);
		assertThat(closedPage.totalElements()).isZero();
	}

	@Test
	void searchMatchesNameCaseInsensitively() {
		jpaRepository.save(newPeriod("Enero-Abril 2026", 2026, 1));
		jpaRepository.save(newPeriod("Mayo-Agosto 2026", 2026, 2));

		PeriodSearchPage byFragment = adapter.search(new PeriodSearchCriteria(null, "enero", 0, 20));

		assertThat(byFragment.totalElements()).isEqualTo(1L);
		assertThat(byFragment.content().get(0).getPeriodNumber()).isEqualTo(1);
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 1; i <= 5; i++) {
			jpaRepository.save(newPeriod("Periodo " + i, 2026, i));
		}

		PeriodSearchPage firstPage = adapter.search(new PeriodSearchCriteria(null, null, 0, 2));
		PeriodSearchPage secondPage = adapter.search(new PeriodSearchCriteria(null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void resultsAreSortedByYearAscendingThenPeriodNumberAscending() {
		jpaRepository.save(newPeriod("Periodo 2027-1", 2027, 1));
		jpaRepository.save(newPeriod("Periodo 2026-2", 2026, 2));
		jpaRepository.save(newPeriod("Periodo 2026-1", 2026, 1));

		PeriodSearchPage page = adapter.search(new PeriodSearchCriteria(null, null, 0, 20));

		assertThat(page.content()).extracting(AcademicPeriod::getYear, AcademicPeriod::getPeriodNumber)
				.containsExactly(tuple(2026, 1), tuple(2026, 2), tuple(2027, 1));
	}

	@Test
	void saveInsertsANewRow() {
		AcademicPeriod saved = adapter.save(newPeriod("Enero-Abril 2026", 2026, 1));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findByYearAndPeriodNumberReturnsMatch() {
		jpaRepository.save(newPeriod("Enero-Abril 2026", 2026, 1));

		assertThat(adapter.findByYearAndPeriodNumber(2026, 1)).isPresent();
		assertThat(adapter.findByYearAndPeriodNumber(2026, 2)).isEmpty();
		assertThat(adapter.findByYearAndPeriodNumber(2027, 1)).isEmpty();
	}

	@Test
	void findByIdReturnsThePeriodWhenItExists() {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril 2026", 2026, 1));

		assertThat(adapter.findById(saved.getId())).isPresent().get()
				.extracting(AcademicPeriod::getName).isEqualTo("Enero-Abril 2026");
	}

	@Test
	void findByIdReturnsEmptyWhenPeriodDoesNotExist() {
		assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
	}

	@Test
	void findAllReturnsAllPeriodsSortedByYearThenPeriodNumber() {
		jpaRepository.save(newPeriod("Periodo 2027-1", 2027, 1));
		jpaRepository.save(newPeriod("Periodo 2026-2", 2026, 2));
		jpaRepository.save(newPeriod("Periodo 2026-1", 2026, 1));

		assertThat(adapter.findAll()).extracting(AcademicPeriod::getYear, AcademicPeriod::getPeriodNumber)
				.containsExactly(tuple(2026, 1), tuple(2026, 2), tuple(2027, 1));
	}

	private static AcademicPeriod newPeriod(String name, int year, int periodNumber) {
		return new AcademicPeriod(name, year, periodNumber, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
				ENROLLMENT_END);
	}
}
