package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.port.in.UpdatePermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.shared.exception.DuplicatePermissionKeyException;
import mx.edu.utez.sisa.identity.shared.exception.PermissionNotFoundException;

public class UpdatePermissionUseCaseImpl implements UpdatePermissionUseCase {

	private final PermissionRepository permissionRepository;

	public UpdatePermissionUseCaseImpl(PermissionRepository permissionRepository) {
		this.permissionRepository = permissionRepository;
	}

	@Override
	public PermissionResult updatePermission(UpdatePermissionCommand command) {
		Permission permission = permissionRepository.findById(command.permissionId())
				.orElseThrow(() -> new PermissionNotFoundException("Permission not found: " + command.permissionId()));

		String key = normalizeKey(command.key());
		if (permissionRepository.existsByKeyAndIdNot(key, permission.getId())) {
			throw new DuplicatePermissionKeyException("Ya existe un permiso registrado con esa clave.");
		}

		permission.updateDetails(normalizeText(command.name()), key);
		Permission saved = permissionRepository.save(permission);
		return new PermissionResult(saved.getId(), saved.getName(), saved.getKey(), saved.getStatus());
	}

	private static String normalizeKey(String key) {
		return normalizeText(key).toUpperCase();
	}

	private static String normalizeText(String text) {
		return text == null ? null : text.trim();
	}
}