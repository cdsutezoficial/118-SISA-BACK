package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

/**
 * Response body for {@code GET /config-academica/statistics}: every counter
 * the dashboard's KPI cards need in one call — no per-module round-trips.
 * {@code actvidad reciente} is out of scope (no audit trail yet).
 */
public record ConfigurationStatisticsResponse(long divisions, long programs, long subjects,
		long groupsForCurrentPeriod, CurrentPeriodResponse currentPeriod) {
}