package mx.edu.utez.sisa.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role_permission", uniqueConstraints = @UniqueConstraint(columnNames = { "role_id", "permission_id" }))
public class RolePermission {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "role_id", nullable = false)
	private UUID roleId;

	@Column(name = "permission_id", nullable = false)
	private UUID permissionId;

	protected RolePermission() {
		// JPA
	}

	public RolePermission(UUID roleId, UUID permissionId) {
		this.roleId = roleId;
		this.permissionId = permissionId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRoleId() {
		return roleId;
	}

	public UUID getPermissionId() {
		return permissionId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof RolePermission that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}