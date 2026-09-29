package mx.edu.utez.sisa.academic_config.domain.port.in;

import java.util.UUID;

/**
 * Dashboard aggregation query: returns every counter the
 * {@code /dashboard} KPI cards need in a single call, so the frontend does not
 * hit one endpoint per module (config-académica). Role authorization is
 * enforced by {@code SecurityFilterConfig}, not here. The "actividad reciente"
 * section is deliberately out of scope — there is no audit trail yet.
 */
public interface GetConfigurationStatisticsUseCase {

	GetConfigurationStatisticsResult getStatistics();

	/**
	 * @param groupsForCurrentPeriod groups of the current period ({@code 0}
	 *                               when no period exists); see {@code currentPeriod}
	 * @param currentPeriod          the active period, or the most recent one when
	 *                               none is active; {@code null} when no period has
	 *                               ever been configured
	 */
	record GetConfigurationStatisticsResult(long divisions, long programs, long subjects,
			long groupsForCurrentPeriod, CurrentPeriodStatistics currentPeriod) {
	}

	record CurrentPeriodStatistics(UUID id, String name) {
	}
}