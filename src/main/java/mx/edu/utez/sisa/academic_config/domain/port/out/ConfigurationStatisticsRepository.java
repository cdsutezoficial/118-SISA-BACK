package mx.edu.utez.sisa.academic_config.domain.port.out;

import java.util.UUID;

/**
 * Aggregation out-port backing {@code GET /config-academica/statistics} — a
 * single read that answers every counter the dashboard (KPI cards) needs, so
 * the frontend does not issue one query per module. Backed by a JPA adapter
 * using plain {@code COUNT} queries; deliberately not packed into the existing
 * per-aggregate repositories because it spans {@code AcademicDivision},
 * {@code AcademicProgram}, {@code Subject} and {@code Group}.
 */
public interface ConfigurationStatisticsRepository {

	long countDivisions();

	long countPrograms();

	long countSubjects();

	/**
	 * @param periodId the period whose groups should be counted
	 * @return number of groups assigned to {@code periodId}
	 */
	long countGroupsForPeriod(UUID periodId);
}