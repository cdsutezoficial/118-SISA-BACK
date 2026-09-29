package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.shared.exception.DuplicatePermissionKeyException;

public class CreatePermissionUseCaseImpl implements CreatePermissionUseCase {

	private final PermissionRepository permissionRepository;

	public CreatePermissionUseCaseImpl(PermissionRepository permissionRepository) {
		this.permissionRepository = permissionRepository;
	}

	@Override
	public PermissionResult createPermission(CreatePermissionCommand command) {
		String key = normalizeKey(command.key());
		if (permissionRepository.existsByKey(key)) {
			throw new DuplicatePermissionKeyException("Ya existe un permiso registrado con esa clave.");
		}

		Permission saved = permissionRepository.save(new Permission(normalizeText(command.name()), key));
		return new PermissionResult(saved.getId(), saved.getName(), saved.getKey(), saved.getStatus());
	}

	private static String normalizeKey(String key) {
		return normalizeText(key).toUpperCase();
	}

	private static String normalizeText(String text) {
		return text == null ? null : text.trim();
	}
}