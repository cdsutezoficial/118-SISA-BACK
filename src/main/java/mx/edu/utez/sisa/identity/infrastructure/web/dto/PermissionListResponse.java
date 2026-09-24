package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.List;

public record PermissionListResponse(List<PermissionListItemResponse> items) {
}