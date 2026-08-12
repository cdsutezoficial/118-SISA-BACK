package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;

/**
 * Request body for {@code PATCH /program-admission-configs/{id}/status}:
 * {@code { "status": "OPEN" | "CLOSED" }}, same shape as
 * {@code ChangeGenerationStatusRequest}.
 */
public record ChangeProgramAdmissionConfigStatusRequest(@NotNull ProgramAdmissionConfigStatus status) {
}
