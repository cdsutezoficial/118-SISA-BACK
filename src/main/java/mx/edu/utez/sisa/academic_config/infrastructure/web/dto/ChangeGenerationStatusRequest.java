package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;

/**
 * Request body for {@code PATCH /generations/{id}/status}:
 * {@code { "status": "ACTIVE" | "FINISHED" }}, same shape as
 * {@code ChangePeriodStatusRequest}.
 */
public record ChangeGenerationStatusRequest(@NotNull GenerationStatus status) {
}
