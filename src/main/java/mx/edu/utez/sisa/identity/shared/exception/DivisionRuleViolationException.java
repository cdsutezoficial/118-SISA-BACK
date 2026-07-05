package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when a role assignment violates the division-scoping rule: a
 * division-scoped {@code RoleType} without a {@code divisionId}, or a
 * non-division {@code RoleType} with one provided (spec: "Assign Role to
 * User").
 */
public class DivisionRuleViolationException extends RuntimeException {

	public DivisionRuleViolationException(String message) {
		super(message);
	}
}
