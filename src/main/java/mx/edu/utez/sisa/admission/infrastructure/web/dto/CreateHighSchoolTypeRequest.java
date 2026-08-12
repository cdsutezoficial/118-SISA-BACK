package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /high-school-types} (mirrors
 * {@code CreateOutreachChannelRequest}). This catalog only has {@code name}.
 */
public record CreateHighSchoolTypeRequest(@NotBlank String name) {
}
