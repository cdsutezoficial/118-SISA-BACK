package mx.edu.utez.sisa.identity.domain.port.out;

import java.util.UUID;

/**
 * Narrow write-only port used by the bootstrap seed to attach a division scope
 * to an already-created role grant.
 *
 * <p>It exists because the two halves of that fact live in different bounded
 * contexts: the division lives in {@code academic_config}, the grant lives in
 * {@code identity}'s {@code user_role}. Rather than have {@code identity} read
 * a foreign catalog (a back door around
 * {@code academic_config.CreateAcademicDivisionUseCase}), the direction is
 * inverted: {@code TestDivisionsSeedRunner} creates the division and
 * {@code TestDirectorScopeSeedRunner} — also in {@code academic_config} — hands
 * the resulting id back through this port, so the {@code user_role} write still
 * happens inside the module that owns the aggregate.
 *
 * <p>Deliberately not part of {@code UserRoleRepository}: that port is the
 * general persistence surface used by the real write paths
 * ({@code AssignRoleUseCase}, {@code RevokeRoleUseCase}), and a
 * seed-only convenience does not belong on it.
 */
public interface UserRoleScopePort {

	/**
	 * Scopes an existing role grant, keyed by the human-readable triple rather
	 * than by ids so the caller does not have to resolve {@code userId} /
	 * {@code roleId} itself.
	 *
	 * <p>Idempotent: re-scoping an already-scoped grant to the same division
	 * reports {@code true} without a write, and re-seeding never has to know
	 * whether the previous run created the grant or found it already there.
	 *
	 * @param username  the account's {@code username} (e.g. {@code director@utez.edu.mx})
	 * @param roleKey   the role's business key (e.g. {@code DIRECTOR_DIVISION})
	 * @param divisionId the division to scope the grant to
	 * @return {@code true} when the grant exists and is scoped to
	 *         {@code divisionId} afterwards; {@code false} when the account or
	 *         the grant does not exist yet
	 */
	boolean scopeDivisionByUsername(String username, String roleKey, UUID divisionId);
}