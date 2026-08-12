package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /outreach-channels} (mirrors
 * {@code CreateSubjectClassificationRequest}). This catalog only has
 * {@code name}.
 */
public record CreateOutreachChannelRequest(@NotBlank String name) {
}
