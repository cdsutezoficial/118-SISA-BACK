package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.port.in.AdvanceAcademicPeriodStatusByDateUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Persists the daily time-driven advancement of every {@code AcademicPeriod}.
 * Thin find-all-then-mutate-then-save shell: the actual forward-only walk
 * lives in {@link AcademicPeriod#advanceByDate}; this use case only touches
 * the repository and counts what changed.
 */
public class AdvanceAcademicPeriodStatusByDateUseCaseImpl implements AdvanceAcademicPeriodStatusByDateUseCase {

	private final AcademicPeriodRepository periodRepository;

	public AdvanceAcademicPeriodStatusByDateUseCaseImpl(AcademicPeriodRepository periodRepository) {
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public int advanceAll(LocalDate today) {
		int changed = 0;
		for (AcademicPeriod period : periodRepository.findAll()) {
			if (period.advanceByDate(today)) {
				periodRepository.save(period);
				changed++;
			}
		}
		return changed;
	}
}