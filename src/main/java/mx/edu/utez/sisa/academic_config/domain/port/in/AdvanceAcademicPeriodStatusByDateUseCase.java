package mx.edu.utez.sisa.academic_config.domain.port.in;

import java.time.LocalDate;

/**
 * Time-driven status advancement for {@code AcademicPeriod}: the daily
 * scheduler counterpart to the manual {@code PATCH /periods/{id}/status}.
 * Iterates every period and lets the clock advance its lifecycle forward
 * per {@code AcademicPeriod#advanceByDate} — {@code ENROLLMENT} once today
 * reaches {@code enrollmentStart}, {@code ACTIVE} once today reaches
 * {@code startDate}, {@code CLOSED} once today passes {@code endDate}. Never
 * moves backward and never touches terminal ({@code CLOSED}) periods.
 */
public interface AdvanceAcademicPeriodStatusByDateUseCase {

	/**
	 * @param today the reference date (injected by the caller so tests can fix
	 *              it; the job passes {@link LocalDate#now})
	 * @return how many periods changed status
	 */
	int advanceAll(LocalDate today);
}