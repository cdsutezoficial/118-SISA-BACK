package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when {@code CreatePersonUseCase} is invoked with an
 * {@code institutionalEmail} that already belongs to another {@code Person}
 * (plan: {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1
 * "chequeados antes del insert").
 */
public class DuplicateInstitutionalEmailException extends RuntimeException {

	public DuplicateInstitutionalEmailException(String message) {
		super(message);
	}
}
