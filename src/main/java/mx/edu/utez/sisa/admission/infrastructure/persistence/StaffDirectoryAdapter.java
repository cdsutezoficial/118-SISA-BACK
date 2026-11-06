package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.StaffDirectoryPort;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads the collector's name from identity: the person behind the user, or the username. */
@Component
public class StaffDirectoryAdapter implements StaffDirectoryPort {

	private final UserRepository userRepository;

	private final PersonRepository personRepository;

	public StaffDirectoryAdapter(UserRepository userRepository, PersonRepository personRepository) {
		this.userRepository = userRepository;
		this.personRepository = personRepository;
	}

	@Override
	public String displayName(UUID userId) {
		if (userId == null) {
			return null;
		}
		return userRepository.findById(userId).map(this::nameOf).orElse(null);
	}

	private String nameOf(User user) {
		if (user.getPersonId() == null) {
			return user.getUsername();
		}
		return personRepository.findById(user.getPersonId()).map(StaffDirectoryAdapter::fullName)
				.filter(name -> !name.isBlank()).orElse(user.getUsername());
	}

	private static String fullName(Person person) {
		return Stream.of(person.getFirstName(), person.getLastName1(), person.getLastName2())
				.filter(Objects::nonNull).map(String::trim).filter(part -> !part.isEmpty())
				.collect(Collectors.joining(" "));
	}
}
