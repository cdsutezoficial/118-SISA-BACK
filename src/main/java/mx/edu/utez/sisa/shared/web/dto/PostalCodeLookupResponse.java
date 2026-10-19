package mx.edu.utez.sisa.shared.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /postal-codes/{postalCode}}.
 *
 * <p>{@code message} es el texto que el formulario de inscripción muestra al
 * aspirante: lo redacta el backend para que el frontend no tenga que mantener
 * copys propios. Nunca describe detalles técnicos.
 */
public record PostalCodeLookupResponse(String postalCode, String message,
		List<PostalCodeLookupItemResponse> items) {
}
