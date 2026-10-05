package mx.edu.utez.sisa.admission.domain.port.out;

import java.util.UUID;

/**
 * Resolves the division scope of the authenticated caller for the candidate
 * list, so RN-ADM-004 ("cada director ve solo candidatos de sus programas") is
 * enforced server-side from the JWT identity rather than trusted from a query
 * parameter a Director could simply omit.
 *
 * <p>Lives in {@code admission} (consumer declares its own port) and is
 * implemented over identity's {@code user_role}/{@code role} tables with a
 * minimal local repository, the same "own minimal read access" convention as
 * {@code ProgramAdmissionConfigLookupJpaRepository}.
 *
 * <p>Note the deliberate use of a tri-state {@link DivisionScope} instead of an
 * {@code Optional<UUID>}: "has the Director role but the grant carries no
 * division" and "does not have the Director role at all" mean opposite things
 * for the list — the first must see NOTHING (fail closed), the second sees
 * EVERYTHING — and an {@code Optional} cannot tell them apart because both
 * collapse to empty.
 */
public interface CallerDivisionScopePort {

	/**
	 * @param callerId the authenticated user's id, taken from the JWT principal
	 * @return {@link DivisionScope#unrestricted()} when the caller does not hold
	 *         {@code DIRECTOR_DIVISION}; otherwise a scoped result whose
	 *         {@code divisionId} may be {@code null}, which the use case must
	 *         treat as "no visible candidates"
	 */
	DivisionScope resolve(UUID callerId);

	/**
	 * @param divisionScoped whether the caller is restricted to one division
	 * @param divisionId     the division to restrict to; only meaningful (and
	 *                       possibly null) when {@code divisionScoped} is true
	 */
	record DivisionScope(boolean divisionScoped, UUID divisionId) {

		public static DivisionScope unrestricted() {
			return new DivisionScope(false, null);
		}

		public static DivisionScope director(UUID divisionId) {
			return new DivisionScope(true, divisionId);
		}
	}
}
