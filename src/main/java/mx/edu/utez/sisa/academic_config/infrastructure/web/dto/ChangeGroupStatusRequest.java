package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;

/**
 * Request body for {@code PATCH /groups/{id}/status}:
 * {@code { "status": "OPEN" | "CLOSED" }}, same shape as
 * {@code ChangeGenerationStatusRequest}.
 */
public record ChangeGroupStatusRequest(@NotNull GroupStatus status) {
}
