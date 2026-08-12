package mx.edu.utez.sisa.shared.web.dto;

import java.util.UUID;

/**
 * A single row of {@code GET /municipalities?stateId=}.
 */
public record MunicipalityListItemResponse(UUID id, String name, String inegiCode) {
}
