package mx.edu.utez.sisa.shared.web.dto;

import java.util.UUID;

/**
 * One settlement choice returned by {@code GET /postal-codes/{postalCode}}.
 */
public record PostalCodeLookupItemResponse(String settlementName, String localityName, String stateName,
		String municipalityName) {
}