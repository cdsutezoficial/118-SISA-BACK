package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;

import java.util.UUID;

/**
 * A single row of {@code GET /outreach-channels}.
 */
public record OutreachChannelListItemResponse(UUID id, String name, OutreachChannelStatus status) {
}
