package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;

/**
 * Request body for {@code PATCH /plans/{id}/status} (design.md — mirrors
 * {@code ChangeProgramStatusRequest}): {@code { "status": "ACTIVE" |
 * "INACTIVE" }}.
 */
public record ChangePlanStatusRequest(@NotNull PlanStatus status) {
}
