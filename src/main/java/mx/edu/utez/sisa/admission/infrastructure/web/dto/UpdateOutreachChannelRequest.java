package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /outreach-channels/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /outreach-channels/{id}/status}.
 */
public record UpdateOutreachChannelRequest(@NotBlank String name) {
}
