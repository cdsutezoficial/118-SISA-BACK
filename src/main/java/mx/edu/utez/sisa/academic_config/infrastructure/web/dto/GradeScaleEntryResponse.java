package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A {@code GradeScaleEntry} owned by a {@code GradeScale}, nested in
 * {@link GradeScaleResponse#entries()}.
 */
public record GradeScaleEntryResponse(UUID id, BigDecimal fromValue, BigDecimal toValue, String letter,
		String description, boolean passed) {
}
