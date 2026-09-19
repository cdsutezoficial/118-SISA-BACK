package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.RoleStatus;

import java.util.UUID;

public interface CreateRoleUseCase {

	RoleResult createRole(CreateRoleCommand command);

	record CreateRoleCommand(String name, String key, String description) {
	}

	record RoleResult(UUID id, String name, String key, RoleStatus status, String description) {
	}
}