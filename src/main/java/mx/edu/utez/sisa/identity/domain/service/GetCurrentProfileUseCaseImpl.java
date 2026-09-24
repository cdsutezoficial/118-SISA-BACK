package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.GetCurrentProfileUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves the acting user's own profile for {@code GET /auth/me}: loads the
 * {@code User} by id (the JWT {@code sub}), joins its linked {@code Person}
 * for the full name, and picks the first non-blank email among
 * {@code institutionalEmail}, {@code personalEmail} and the username (the
 * seeds use the institutional email as username, so there is always a
 * non-null answer).
 */
public class GetCurrentProfileUseCaseImpl implements GetCurrentProfileUseCase {

	private final UserRepository userRepository;
	private final PersonRepository personRepository;

	public GetCurrentProfileUseCaseImpl(UserRepository userRepository, PersonRepository personRepository) {
		this.userRepository = userRepository;
		this.personRepository = personRepository;
	}

	@Override
	public CurrentProfileResult getCurrentProfile(CurrentProfileQuery query) {
		User user = userRepository.findById(query.userId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + query.userId()));
		Person person = personRepository.findById(user.getPersonId()).orElse(null);
		return new CurrentProfileResult(user.getId(), fullName(person), user.getUsername(),
				firstEmail(person, user));
	}

	/**
	 * Same {@code firstName lastName1 [lastName2]} join as
	 * {@code GetUserUseCaseImpl#fullName} and
	 * {@code ListUsersUseCaseImpl#fullName} — kept local per this module's
	 * convention of each service owning its own small mapping helper.
	 */
	private static String fullName(Person person) {
		if (person == null) {
			return "";
		}
		return Stream.of(person.getFirstName(), person.getLastName1(), person.getLastName2())
				.filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
	}

	private static String firstEmail(Person person, User user) {
		if (person != null) {
			String institutional = person.getInstitutionalEmail();
			if (institutional != null && !institutional.isBlank()) {
				return institutional;
			}
			String personal = person.getPersonalEmail();
			if (personal != null && !personal.isBlank()) {
				return personal;
			}
		}
		return user.getUsername();
	}
}