package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.MissingInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.PersonAlreadyHasUserException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a staff {@code User} account for a pre-existing {@code Person}
 * (spec: "Create User Account"). Person creation itself is out of scope
 * (design.md — Decision: Person creation is out of scope), so the referenced
 * {@code Person} is only loaded, never created here.
 */
public class CreateUserUseCaseImpl implements CreateUserUseCase {

	private final UserRepository userRepository;
	private final PersonRepository personRepository;
	private final PasswordHasher passwordHasher;

	public CreateUserUseCaseImpl(UserRepository userRepository, PersonRepository personRepository,
			PasswordHasher passwordHasher) {
		this.userRepository = userRepository;
		this.personRepository = personRepository;
		this.passwordHasher = passwordHasher;
	}

	@Override
	@Transactional
	public UserCreationResult createUser(CreateUserCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		if (userRepository.findByPersonId(command.personId()).isPresent()) {
			throw new PersonAlreadyHasUserException(
					"Person already has a user account: " + command.personId());
		}

		Person person = personRepository.findById(command.personId())
				.orElseThrow(() -> new UserNotFoundException("Person not found: " + command.personId()));

		if (person.getInstitutionalEmail() == null) {
			throw new MissingInstitutionalEmailException(
					"Person has no institutional email: " + command.personId());
		}

		String passwordHash = passwordHasher.hash(command.temporaryPassword());
		User user = new User(person.getId(), person.getInstitutionalEmail(), passwordHash);
		User saved = userRepository.save(user);

		return new UserCreationResult(saved.getId(), saved.getUsername(), saved.isMustChangePassword());
	}
}
