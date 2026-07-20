package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /subject-classifications/{id}} (Phase 4 —
 * "Actualización"). {@code status} is deliberately absent — status
 * transitions go through the future {@code PATCH /subject-classifications/{id}/status}
 * (Phase 5).
 */
public record UpdateSubjectClassificationRequest(@NotBlank String name, @NotBlank String code) {
}
