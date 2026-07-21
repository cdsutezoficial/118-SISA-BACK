package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link AcademicPeriodRepository} adapter delegating to
 * {@link AcademicPeriodJpaRepository}. Results are sorted by {@code year}
 * ascending then {@code periodNumber} ascending — the natural chronological
 * reading order for academic periods, and deterministic across pages since
 * {@code (year, periodNumber)} is unique.
 */
@Component
public class AcademicPeriodRepositoryAdapter implements AcademicPeriodRepository {

	private final AcademicPeriodJpaRepository jpaRepository;

	public AcademicPeriodRepositoryAdapter(AcademicPeriodJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public AcademicPeriod save(AcademicPeriod period) {
		return jpaRepository.save(period);
	}

	@Override
	public Optional<AcademicPeriod> findByYearAndPeriodNumber(int year, int periodNumber) {
		return jpaRepository.findByYearAndPeriodNumber(year, periodNumber);
	}

	@Override
	public Optional<AcademicPeriod> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public PeriodSearchPage search(PeriodSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "year").and(Sort.by(Sort.Direction.ASC, "periodNumber")));
		Page<AcademicPeriod> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new PeriodSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
