package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import mx.edu.utez.sisa.identity.domain.model.RoleStatus;

import java.util.UUID;

public record RoleListItemResponse(UUID id, String name, String key, RoleStatus status, String description) {
}