package mx.edu.utez.sisa.finance.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only out-port over {@code Person} (shared kernel) used to validate that
 * the subject of a charge exists before quoting or creating an adeudo.
 *
 * <p>Only the identity fields reporting needs are projected — the quoter
 * never touches the person's profiles.
 */
public interface PersonQueryPort {

	Optional<PersonView> findById(UUID id);

	record PersonView(UUID id, String curp, String firstName, String lastName1, String lastName2) {
	}
}
