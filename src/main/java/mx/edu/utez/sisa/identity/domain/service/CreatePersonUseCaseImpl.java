package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateCurpException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a {@code Person} for the manual internal-staff registration flow
 * (plan: {@code docs/plans/2026-07-28-persons-and-user-management.md} —
 * 4.1). Distinct from the {@code AdminSeedRunner}/test-fixture usage of
 * {@code PersonRepository#save} that originally motivated "Person creation
 * is out of scope" in design.md — this is now a real, ADMIN-facing use
 * case, following the caller-lookup/{@code assertCanOperate} shape already
 * established by {@code CreateUserUseCaseImpl}/{@code AssignRoleUseCaseImpl}.
 */
public class CreatePersonUseCaseImpl implements CreatePersonUseCase {

	private final UserRepository userRepository;
	private final PersonRepository personRepository;

	public CreatePersonUseCaseImpl(UserRepository userRepository, PersonRepository personRepository) {
		this.userRepository = userRepository;
		this.personRepository = personRepository;
	}

	@Override
	@Transactional
	public PersonResult createPerson(CreatePersonCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		if (personRepository.findByCurp(command.curp()).isPresent()) {
			throw new DuplicateCurpException("Ya existe una persona registrada con esa información.");
		}
		if (personRepository.findByInstitutionalEmail(command.institutionalEmail()).isPresent()) {
			throw new DuplicateInstitutionalEmailException("Ya existe una persona registrada con esa información.");
		}

		Person person = new Person(command.curp(), command.firstName(), command.lastName1(), command.lastName2(),
				command.institutionalEmail());
		Person saved = personRepository.save(person);

		return toResult(saved);
	}

	private static PersonResult toResult(Person person) {
		return new PersonResult(person.getId(), person.getCurp(), person.getFirstName(), person.getLastName1(),
				person.getLastName2(), person.getInstitutionalEmail());
	}
}
