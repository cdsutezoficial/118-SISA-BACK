package mx.edu.utez.sisa.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * A single scoped role grant for a {@link User}. Division-required-vs-forbidden
 * validation is out of scope for this data holder — it belongs to
 * {@code AssignRoleUseCaseImpl} (design.md — File Changes table), which is the
 * only writer of new instances.
 */
@Entity
@Table(name = "user_role")
public class UserRole {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID userId;

	@Column(name = "role_id", nullable = false)
	private UUID roleId;

	@Column(name = "role_type", insertable = false, updatable = false)
	private String legacyRoleKey;

	@Column
	private UUID divisionId;

	protected UserRole() {
		// JPA
	}

	public UserRole(UUID userId, UUID roleId, UUID divisionId) {
		this.userId = userId;
		this.roleId = roleId;
		this.divisionId = divisionId;
	}

	/**
	 * Re-points an existing grant at a division. Used only to backfill the
	 * scope on an already-seeded {@code DIRECTOR_DIVISION} account, whose grant
	 * is created with a {@code null} division because
	 * {@code identity.TestAccountsSeedRunner} cannot see which division exists
	 * ({@code academic_config} owns that catalog).
	 *
	 * <p>The required-vs-forbidden rule is NOT enforced here, for the same
	 * reason it is not enforced on the constructor: this data holder stores no
	 * role key, so it cannot know whether the grant may carry a division. The
	 * caller is responsible — {@code AssignRoleUseCaseImpl} for the real write
	 * path, {@code UserRoleScopeAdapter} for the seed, which is why the seed
	 * only ever scopes {@code DIRECTOR_DIVISION} (a division-scoped role).
	 */
	public void scopeDivision(UUID divisionId) {
		this.divisionId = divisionId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getRoleId() {
		return roleId;
	}

	public String getLegacyRoleKey() {
		return legacyRoleKey;
	}

	public UUID getDivisionId() {
		return divisionId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof UserRole userRole)) {
			return false;
		}
		return id != null && id.equals(userRole.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
