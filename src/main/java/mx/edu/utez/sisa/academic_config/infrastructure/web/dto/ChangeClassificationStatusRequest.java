package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;

/**
 * Request body for {@code PATCH /subject-classifications/{id}/status}
 * (Phase 5 — ChangeStatus): {@code { "status": "ACTIVE" | "INACTIVE" }},
 * same shape as {@code ChangeDivisionStatusRequest}.
 */
public record ChangeClassificationStatusRequest(@NotNull ClassificationStatus status) {
}
