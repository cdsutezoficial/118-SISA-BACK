package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;

/**
 * Request body for {@code PATCH /high-school-types/{id}/status}:
 * {@code { "status": "ACTIVE" | "INACTIVE" }}, same shape as
 * {@code ChangeOutreachChannelStatusRequest}.
 */
public record ChangeHighSchoolTypeStatusRequest(@NotNull HighSchoolTypeStatus status) {
}
