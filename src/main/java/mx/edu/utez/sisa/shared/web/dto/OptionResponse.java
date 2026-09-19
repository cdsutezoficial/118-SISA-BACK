package mx.edu.utez.sisa.shared.web.dto;

import java.util.UUID;

/**
 * Canonical minimal projection for the reference-catalog endpoints
 * "{@code GET /{resource}/options}" (transversal design: "Roles y Permisos —
 * patrón reference"). Every reference picker returns items shaped exactly
 * like this — {@code id} + {@code label} (what the selector shows) + optional
 * {@code code} (secondary identifier some pickers display). Never the list or
 * detail DTO; never management or sensitive fields.
 *
 * <p>Resides in {@code shared.web.dto} because the shape is common to every
 * bounded context's reference catalogs, and the option list is returned as a
 * bare JSON array (no wrapper envelope).
 */
public record OptionResponse(UUID id, String label, String code) {
}