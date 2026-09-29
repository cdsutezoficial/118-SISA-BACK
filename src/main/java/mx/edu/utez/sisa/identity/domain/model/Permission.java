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
@Table(name = "permission")
public class Permission {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "permission_key", nullable = false, unique = true)
	private String key;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PermissionStatus status;

	protected Permission() {
		// JPA
	}

	public Permission(String name, String key) {
		this.name = name;
		this.key = key;
		this.status = PermissionStatus.ACTIVE;
	}

	public void updateDetails(String name, String key) {
		this.name = name;
		this.key = key;
	}

	public void activate() {
		this.status = PermissionStatus.ACTIVE;
	}

	public void deactivate() {
		this.status = PermissionStatus.INACTIVE;
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

	public PermissionStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Permission that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}