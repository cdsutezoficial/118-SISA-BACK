package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /payment-areas/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /payment-areas/{id}/status}.
 *
 * <p><b>Las reglas de {@code name}, {@code code} y {@code description} están
 * duplicadas literalmente de {@link CreatePaymentAreaRequest} y deben coincidir
 * carácter a carácter.</b> No se pueden compartir porque el módulo no tiene
 * validación compuesta entre records: si un alta acepta un código de 6
 * caracteres y una edición no, el usuario crea un área que después no puede
 * corregir. Es la misma deuda que ya arrastra
 * {@code UpdateAcademicDivisionRequest}, y el mismo criterio: divergir entre
 * ambos DTOs es un 400 que sólo aparece en uno de los dos caminos.
 *
 * <p>El {@code @Pattern} de {@code code} ({@code ^[A-Z0-9]{2,5}$}) y el motivo
 * de que exija mayúscula están explicados en el javadoc de
 * {@link CreatePaymentAreaRequest}.
 */
public record UpdatePaymentAreaRequest(
		@NotBlank(message = "El nombre del área es obligatorio.") @Size(max = 150, message = "El nombre del área no puede superar 150 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre del área contiene caracteres no válidos.") String name,
		@NotBlank(message = "La clave del área es obligatoria.") @Size(min = 2, max = 5, message = "La clave del área debe tener entre 2 y 5 caracteres.") @Pattern(regexp = "^[A-Z0-9]{2,5}$", message = "La clave del área solo puede contener letras mayúsculas y números.") String code,
		@Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción contiene caracteres no válidos.") String description) {
}
