package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.port.in.UpdateRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateRoleKeyException;
import mx.edu.utez.sisa.identity.shared.exception.RoleNotFoundException;

public class UpdateRoleUseCaseImpl implements UpdateRoleUseCase {

	private final RoleRepository roleRepository;

	public UpdateRoleUseCaseImpl(RoleRepository roleRepository) {
		this.roleRepository = roleRepository;
	}

	@Override
	public RoleResult updateRole(UpdateRoleCommand command) {
		Role role = roleRepository.findById(command.roleId())
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + command.roleId()));

		String key = normalizeKey(command.key());
		if (roleRepository.existsByKeyAndIdNot(key, role.getId())) {
			throw new DuplicateRoleKeyException("Ya existe un rol registrado con esa clave.");
		}

		role.updateDetails(normalizeText(command.name()), key, normalizeText(command.description()));
		Role saved = roleRepository.save(role);
		return new RoleResult(saved.getId(), saved.getName(), saved.getKey(), saved.getStatus(), saved.getDescription());
	}

	private static String normalizeKey(String key) {
		return normalizeText(key).toUpperCase();
	}

	private static String normalizeText(String text) {
		return text == null ? null : text.trim();
	}
}