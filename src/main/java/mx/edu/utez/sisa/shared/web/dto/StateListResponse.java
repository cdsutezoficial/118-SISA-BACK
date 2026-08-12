package mx.edu.utez.sisa.shared.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /states}: {@code { items: [] } } — full
 * catalog, no pagination (only 32 fixed rows).
 */
public record StateListResponse(List<StateListItemResponse> items) {
}
