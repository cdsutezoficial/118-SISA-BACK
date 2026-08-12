package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;

import java.util.UUID;

/**
 * Response body for {@code POST}/{@code PUT}/{@code GET}/{@code PATCH}
 * {@code .../status} on {@code /outreach-channels} — mirrors
 * {@code SubjectClassificationResponse}'s "full post-operation state"
 * convention.
 */
public record OutreachChannelResponse(UUID id, String name, OutreachChannelStatus status) {
}
