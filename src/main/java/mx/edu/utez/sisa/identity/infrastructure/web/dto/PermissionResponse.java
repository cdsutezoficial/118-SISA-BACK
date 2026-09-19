package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

import java.util.UUID;

public record PermissionResponse(UUID id, String name, String key, PermissionStatus status) {
}