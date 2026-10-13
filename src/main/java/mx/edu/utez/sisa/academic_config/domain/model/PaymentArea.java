package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

/**
 * Payment area catalog aggregate root — companion catalog to
 * {@link PaymentConcept}: an area groups payment concepts (e.g. "Cuotas de
 * Inscripción", "Colegiaturas"). Lives in {@code academic_config} next to
 * {@code PaymentConcept}, same placement rationale (the real Finance bounded
 * context is not part of this phase).
 *
 * <p>
 * Unlike {@code PaymentConcept}, this aggregate has a {@code code} field and
 * both {@code name} and {@code code} are unique business keys (same shape as
 * {@link AcademicDivision}): uniqueness is enforced in the domain through
 * {@code findByCode}/{@code findByName} plus {@code DuplicatePaymentAreaCodeException}
 * /{@code DuplicatePaymentAreaNameException}, not only by the DB constraint.
 *
 * <p>
 * {@code description} is mapped as a {@code TEXT} column (same rationale as
 * {@code PaymentConcept#description}) since it is a free-form field.
 *
 * <p>
 * <b>Las dos unicidades van como {@code @UniqueConstraint} nombradas y no como
 * {@code @Column(unique = true)}</b> (Fase 11). Con {@code unique = true}
 * Hibernate genera un índice con nombre autogenerado, y el mensaje de error que
 * ve el desarrollador cuando una alta choca contra él cambia entre entornos. Con
 * nombre fijo ({@code uk_payment_area_name} / {@code uk_payment_area_code}) el
 * diagnóstico es el mismo en local, en pruebas y en producción, y coincide con
 * la convención ya usada por {@code OutreachChannel} y
 * {@code HighSchoolType}.
 *
 * <p>
 * <b>Las longitudes son explícitas</b> por la misma razón: sin {@code length},
 * {@code name} y {@code code} salían como {@code VARCHAR(255)} implícito. El
 * {@code 150} de {@code name} es el mismo techo que usan los catálogos hermanos
 * ({@code AcademicDivision}, {@code OutreachChannel}, {@code HighSchoolType}); el
 * {@code 5} de {@code code} viene de la regla de negocio confirmada el
 * 2026-10-05 (2 a 5 alfanuméricos en mayúscula). Los valores por defecto del
 * DTO ({@code @Size}) deben coincidir carácter a carácter con estos
 * {@code length}, o el backend aceptaría un valor que MySQL trunca.
 */
@Entity
@Table(name = "payment_area", uniqueConstraints = {
		@UniqueConstraint(name = "uk_payment_area_name", columnNames = "name"),
		@UniqueConstraint(name = "uk_payment_area_code", columnNames = "code") })
public class PaymentArea {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "code", nullable = false, length = 5)
	private String code;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentAreaStatus status;

	protected PaymentArea() {
		// JPA
	}

	public PaymentArea(String name, String code, String description) {
		this.name = name;
		this.code = code;
		this.description = description;
		this.status = PaymentAreaStatus.ACTIVE;
	}

	/**
	 * Updates the catalog fields (Update use case). {@code status} is
	 * deliberately absent: status transitions are the sole responsibility of
	 * {@link #activate()}/{@link #deactivate()}, same separation as
	 * {@code PaymentConcept#updateDetails}.
	 */
	public void updateDetails(String name, String code, String description) {
		this.name = name;
		this.code = code;
		this.description = description;
	}

	/**
	 * Transitions to {@code ACTIVE}. Idempotent — calling on an
	 * already-{@code ACTIVE} area is a no-op.
	 */
	public void activate() {
		this.status = PaymentAreaStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code INACTIVE}. Idempotent — calling on an
	 * already-{@code INACTIVE} area is a no-op. The record itself is never
	 * deleted.
	 */
	public void deactivate() {
		this.status = PaymentAreaStatus.INACTIVE;
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

	public String getDescription() {
		return description;
	}

	public PaymentAreaStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PaymentArea that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
