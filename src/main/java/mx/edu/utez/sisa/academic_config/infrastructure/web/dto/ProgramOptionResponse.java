package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * Response body for {@code GET /programs/options}: the canonical
 * {@link mx.edu.utez.sisa.shared.web.dto.OptionResponse} projection plus
 * {@code modality}. The extra field is what lets reference pickers render the
 * same "{@code DSM — Desarrollo de Software (Presencial)}" label as the
 * management list ({@code GET /programs}) without pulling the full page DTO —
 * {@code /programs} requires ADMIN/SERVICIOS_ESCOLARES while this endpoint is
 * deliberately {@code authenticated()} so roles like PERSONAL_FINANZAS can
 * still fill their carrera picker.
 *
 * <p>A dedicated record instead of widening the shared {@code OptionResponse}
 * keeps the shape of every other {@code /{resource}/options} endpoint
 * untouched (design.md — reference catalogs: {@code id} + {@code label} +
 * optional {@code code}).
 */
public record ProgramOptionResponse(UUID id, String label, String code, ProgramModality modality) {
}
