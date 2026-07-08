package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;

/**
 * Request body for {@code PATCH /programs/{id}/status} (design.md — Decision:
 * single status-change use case + idempotent PATCH): {@code
 * { "status": "ACTIVE" | "INACTIVE" }}.
 */
public record ChangeProgramStatusRequest(@NotNull ProgramStatus status) {
}
