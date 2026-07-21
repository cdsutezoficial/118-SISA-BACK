package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;

/**
 * Request body for {@code PATCH /periods/{id}/status}:
 * {@code { "status": "CONFIGURATION" | "ENROLLMENT" | "ACTIVE" | "CLOSED" }},
 * same shape as {@code ChangeClassificationStatusRequest}.
 */
public record ChangePeriodStatusRequest(@NotNull PeriodStatus status) {
}
