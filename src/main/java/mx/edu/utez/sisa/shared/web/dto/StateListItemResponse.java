package mx.edu.utez.sisa.shared.web.dto;

import java.util.UUID;

/**
 * A single row of {@code GET /states}.
 */
public record StateListItemResponse(UUID id, String name, String inegiCode) {
}
