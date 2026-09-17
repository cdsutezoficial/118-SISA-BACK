package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.RoleStatus;

import java.util.UUID;

public interface ChangeRoleStatusUseCase {

	RoleResult changeStatus(ChangeRoleStatusCommand command);

	record ChangeRoleStatusCommand(UUID callerId, UUID roleId, RoleStatus status) {
	}

	record RoleResult(UUID id, String name, String key, RoleStatus status, String description) {
	}
}