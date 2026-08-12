package mx.edu.utez.sisa.shared.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /municipalities?stateId=}: {@code { items: [] } }
 * — the requested state's municipalities, no pagination (Oaxaca, the largest
 * real case, has 570).
 */
public record MunicipalityListResponse(List<MunicipalityListItemResponse> items) {
}
