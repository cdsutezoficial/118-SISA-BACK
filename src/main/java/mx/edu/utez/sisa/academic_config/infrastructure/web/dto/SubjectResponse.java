package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;

import java.util.UUID;

/**
 * A {@code Subject} owned by a {@code PlanLevel}, nested in
 * {@link PlanLevelResponse#subjects()} or returned directly by the
 * {@code POST}/{@code PUT} subject endpoints.
 */
public record SubjectResponse(UUID id, String code, String name, int credits, int weeklyHours, int evaluationUnits,
		int displayOrder, SubjectType type, boolean isRetakeable, UUID classificationId) {
}
