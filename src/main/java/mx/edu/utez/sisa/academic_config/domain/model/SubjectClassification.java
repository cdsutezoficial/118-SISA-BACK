package mx.edu.utez.sisa.academic_config.domain.model;

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

/**
 * Subject classification catalog aggregate root (source:
 * {@code 02-config-academica.md} lines 15-24 — "Catálogo de clasificaciones
 * de materias"). Determines which grade scale equivalence (numeric → letter)
 * applies when a subject is evaluated. Unlike {@link AcademicDivision},
 * {@code name} is deliberately NOT unique — only {@code code} is (domain doc
 * does not mark {@code name} as a uniqueness constraint).
 *
 * <p>Phase 1 of this aggregate only covers the read (List) side — see
 * {@code docs/plans/2026-07-15-subject-classification-crud.md}. Create,
 * Update, and status-transition behavior are intentionally not implemented
 * yet (YAGNI): they are added in their own phase once the frontend consumes
 * this endpoint.
 */
@Entity
@Table(name = "subject_classification")
public class SubjectClassification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ClassificationStatus status;

	protected SubjectClassification() {
		// JPA
	}

	public SubjectClassification(String name, String code) {
		this.name = name;
		this.code = code;
		this.status = ClassificationStatus.ACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCode() {
		return code;
	}

	public ClassificationStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof SubjectClassification that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
