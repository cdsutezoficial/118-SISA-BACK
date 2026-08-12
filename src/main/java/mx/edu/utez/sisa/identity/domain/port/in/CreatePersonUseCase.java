package mx.edu.utez.sisa.identity.domain.port.in;

import java.util.UUID;

/**
 * Creates a {@code Person} for internal-staff registration (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1,
 * José's 2026-07-28 decision that a manual internal-staff intake flow is
 * needed, separate from Admission's candidate registration form). Restricted
 * to ADMIN callers; implementations must enforce the caller's
 * {@code assertCanOperate()} gate, same as {@code CreateUserUseCase}/
 * {@code AssignRoleUseCase}.
 */
public interface CreatePersonUseCase {

	PersonResult createPerson(CreatePersonCommand command);

	/**
	 * @param callerId            the acting ADMIN user, used for the mustChangePassword guard and authorization
	 * @param curp                must be unique across all Persons
	 * @param firstName           required
	 * @param lastName1           required
	 * @param lastName2           optional
	 * @param institutionalEmail  required for this endpoint (plan 4.1: without it, no User can ever be
	 *                            created for this Person — {@code MissingInstitutionalEmailException}), must
	 *                            be unique across all Persons
	 */
	record CreatePersonCommand(UUID callerId, String curp, String firstName, String lastName1, String lastName2,
			String institutionalEmail) {
	}

	record PersonResult(UUID personId, String curp, String firstName, String lastName1, String lastName2,
			String institutionalEmail) {
	}
}
