package mx.edu.utez.sisa.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.RoleType;

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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RoleType roleType;

	@Column
	private UUID divisionId;

	protected UserRole() {
		// JPA
	}

	public UserRole(UUID userId, RoleType roleType, UUID divisionId) {
		this.userId = userId;
		this.roleType = roleType;
		this.divisionId = divisionId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public RoleType getRoleType() {
		return roleType;
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
