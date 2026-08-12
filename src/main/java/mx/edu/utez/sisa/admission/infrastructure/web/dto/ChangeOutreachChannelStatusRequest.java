package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;

/**
 * Request body for {@code PATCH /outreach-channels/{id}/status}:
 * {@code { "status": "ACTIVE" | "INACTIVE" }}, same shape as
 * {@code ChangeClassificationStatusRequest}.
 */
public record ChangeOutreachChannelStatusRequest(@NotNull OutreachChannelStatus status) {
}
