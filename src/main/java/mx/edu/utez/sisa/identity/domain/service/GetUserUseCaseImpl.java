package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.GetUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Full-detail lookup for a single {@code User} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.3).
 * Unlike {@code ListUsersUseCaseImpl}, this maps every {@link UserRole} row
 * with its own {@code userRoleId} exposed — needed by
 * {@code RevokeRoleUseCase} downstream from the detail screen.
 */
public class GetUserUseCaseImpl implements GetUserUseCase {

	private final UserRepository userRepository;
	private final PersonRepository personRepository;
	private final UserRoleRepository userRoleRepository;

	public GetUserUseCaseImpl(UserRepository userRepository, PersonRepository personRepository,
			UserRoleRepository userRoleRepository) {
		this.userRepository = userRepository;
		this.personRepository = personRepository;
		this.userRoleRepository = userRoleRepository;
	}

	@Override
	public UserDetailResult getUser(GetUserQuery query) {
		User caller = userRepository.findById(query.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + query.callerId()));
		caller.assertCanOperate();

		User target = userRepository.findById(query.userId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + query.userId()));
		Person person = personRepository.findById(target.getPersonId()).orElse(null);

		var roles = userRoleRepository.findByUserId(target.getId()).stream()
				.map(role -> new UserRoleDetail(role.getId(), role.getRoleType(), role.getDivisionId())).toList();

		return new UserDetailResult(target.getId(), target.getPersonId(), fullName(person), target.getUsername(),
				target.getStatus(), target.isMustChangePassword(), target.getLastLoginAt(), target.getCreatedAt(),
				roles);
	}

	/**
	 * Same {@code firstName lastName1 [lastName2]} join as
	 * {@code ListUsersUseCaseImpl#fullName} — duplicated rather than
	 * extracted, matching this module's existing convention of each service
	 * owning its own small mapping helper (no shared mapper class exists
	 * today).
	 */
	private static String fullName(Person person) {
		if (person == null) {
			return "";
		}
		return Stream.of(person.getFirstName(), person.getLastName1(), person.getLastName2())
				.filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
	}
}
