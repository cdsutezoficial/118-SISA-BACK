package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link AcademicPeriod} — same shape as
 * {@code SubjectClassificationRepository}: {@link #save}, an existence lookup
 * for the aggregate's uniqueness rule ({@link #findByYearAndPeriodNumber}, in
 * place of {@code findByCode}), {@link #findById}, and a filterable paginated
 * {@link #search(PeriodSearchCriteria)}.
 */
public interface AcademicPeriodRepository {

	AcademicPeriod save(AcademicPeriod period);

	/**
	 * Backs the {@code (year, periodNumber)} uniqueness rule (plan §4) — same
	 * convention as {@code SubjectClassificationRepository#findByCode}.
	 */
	Optional<AcademicPeriod> findByYearAndPeriodNumber(int year, int periodNumber);

	Optional<AcademicPeriod> findById(UUID id);

	/**
	 * Filterable, paginated query backing {@code ListAcademicPeriodsUseCase}.
	 */
	PeriodSearchPage search(PeriodSearchCriteria criteria);

	/**
	 * @param status optional — filters to periods with this exact status
	 * @param search optional free-text match against {@code name}
	 * @param page   zero-based page index
	 * @param size   page size
	 */
	record PeriodSearchCriteria(PeriodStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link AcademicPeriod} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record PeriodSearchPage(List<AcademicPeriod> content, long totalElements, int totalPages) {
	}
}
