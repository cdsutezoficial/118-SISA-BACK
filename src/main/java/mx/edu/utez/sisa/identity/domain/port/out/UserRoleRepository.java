package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link UserRole}. Implemented by a JPA adapter in
 * Phase 4.
 */
public interface UserRoleRepository {

	UserRole save(UserRole userRole);

	List<UserRole> findByUserId(UUID userId);

	/**
	 * Used by {@code AdminSeedRunner} (design.md — Decision: Bootstrap ADMIN
	 * seed) to detect whether any {@code ADMIN} role already exists, instead
	 * of checking table emptiness.
	 */
	boolean existsByRoleId(UUID roleId);

	/**
	 * Batch-loads every role grant for a page of users (used by
	 * {@code ListUsersUseCaseImpl} to attach {@code UserRole[]} to each result
	 * row without one query per user).
	 */
	List<UserRole> findByUserIdIn(List<UUID> userIds);

	/**
	 * Backs {@code GetUserUseCase} (single-role lookup for
	 * {@code userRoleId}) and {@code RevokeRoleUseCase} (plan:
	 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.4),
	 * which loads the {@link UserRole} first to validate it belongs to the
	 * {@code userId} in the URL before deleting it.
	 */
	Optional<UserRole> findById(UUID id);

	/**
	 * Backs {@code RevokeRoleUseCase}'s deletion step. {@code UserRole} is the
	 * only entity in this module with a real hard-delete path — unlike
	 * {@code User}/{@code Person}, which are never removed, only
	 * status-transitioned.
	 */
	void delete(UserRole userRole);
}
