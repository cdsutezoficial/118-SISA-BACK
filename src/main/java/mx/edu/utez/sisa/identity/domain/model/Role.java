package mx.edu.utez.sisa.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role")
public class Role {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "role_key", nullable = false, unique = true)
	private String key;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RoleStatus status;

	@Column(nullable = false)
	private String description;

	protected Role() {
		// JPA
	}

	public Role(String name, String key, String description) {
		this.name = name;
		this.key = key;
		this.description = description;
		this.status = RoleStatus.ACTIVE;
	}

	public void updateDetails(String name, String key, String description) {
		this.name = name;
		this.key = key;
		this.description = description;
	}

	public void activate() {
		this.status = RoleStatus.ACTIVE;
	}

	public void deactivate() {
		this.status = RoleStatus.INACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getKey() {
		return key;
	}

	public RoleStatus getStatus() {
		return status;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Role role)) {
			return false;
		}
		return id != null && id.equals(role.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}