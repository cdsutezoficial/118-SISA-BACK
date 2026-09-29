package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.UUID;

/**
 * The current period for the dashboard — which one the "grupos" counter is
 * scoped to. {@code null} when no period has ever been configured.
 */
public record CurrentPeriodResponse(UUID id, String name) {
}