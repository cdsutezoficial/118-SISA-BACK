package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;

import java.util.UUID;

/**
 * Response body for {@code POST}/{@code PUT}/{@code GET}/{@code PATCH}
 * {@code .../status} on {@code /high-school-types} — mirrors
 * {@code OutreachChannelResponse}'s "full post-operation state" convention.
 */
public record HighSchoolTypeResponse(UUID id, String name, HighSchoolTypeStatus status) {
}
