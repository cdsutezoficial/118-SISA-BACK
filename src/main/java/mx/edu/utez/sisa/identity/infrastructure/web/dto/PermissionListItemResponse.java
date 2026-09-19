package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

import java.util.UUID;

public record PermissionListItemResponse(UUID id, String name, String key, PermissionStatus status) {
}