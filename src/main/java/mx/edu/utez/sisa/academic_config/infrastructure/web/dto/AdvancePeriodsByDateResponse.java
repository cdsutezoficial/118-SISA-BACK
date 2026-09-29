package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

/**
 * Response body for {@code POST /periods/advance-by-date}: how many academic
 * periods actually advanced through the date-based thresholds in
 * {@code AcademicPeriod#advanceByDate}. The front calls this endpoint when
 * the list opens so the displayed statuses are current without waiting for
 * the daily {@code AdvanceAcademicPeriodStatusJob}.
 */
public record AdvancePeriodsByDateResponse(int advanced) {
}