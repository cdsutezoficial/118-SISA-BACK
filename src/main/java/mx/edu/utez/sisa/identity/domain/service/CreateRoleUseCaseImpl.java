package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.port.in.CreateRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateRoleKeyException;

public class CreateRoleUseCaseImpl implements CreateRoleUseCase {

	private final RoleRepository roleRepository;

	public CreateRoleUseCaseImpl(RoleRepository roleRepository) {
		this.roleRepository = roleRepository;
	}

	@Override
	public RoleResult createRole(CreateRoleCommand command) {
		String key = normalizeKey(command.key());
		if (roleRepository.existsByKey(key)) {
			throw new DuplicateRoleKeyException("Ya existe un rol registrado con esa clave.");
		}

		Role saved = roleRepository.save(new Role(normalizeText(command.name()), key, normalizeText(command.description())));
		return new RoleResult(saved.getId(), saved.getName(), saved.getKey(), saved.getStatus(), saved.getDescription());
	}

	private static String normalizeKey(String key) {
		return normalizeText(key).toUpperCase();
	}

	private static String normalizeText(String text) {
		return text == null ? null : text.trim();
	}
}