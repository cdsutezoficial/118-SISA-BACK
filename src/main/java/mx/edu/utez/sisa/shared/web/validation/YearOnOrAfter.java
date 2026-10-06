package mx.edu.utez.sisa.shared.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

/**
 * The annotated {@link LocalDate} is not earlier than a floor year.
 *
 * <p>Hibernate Validator ships {@code @Past}, {@code @Future} and their
 * "or present" pairs, but nothing for a year floor, and the DTOs need one:
 * a plan's {@code effectiveFrom} may sit in the future (it starts on the next
 * cuatrimestre), so {@code @PastOrPresent} is wrong, while {@code 1900} or
 * {@code 0001} is typing noise that would silently become the plan's vigencia.
 *
 * <p>The format itself is not this annotation's job: Jackson rejects an
 * unparsable date with {@code HttpMessageNotReadableException} (400), and the
 * frontend's date mask enforces {@code dd/mm/yyyy} before it ever ships.
 *
 * <p>{@code null} passes — presence belongs to the field's {@code @NotNull},
 * and a validator that also enforced presence would duplicate it (and pick a
 * different message than the DTO's).
 *
 * @see mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateAcademicPlanRequest
 */
@Documented
@Target({ FIELD, PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = YearOnOrAfter.Validator.class)
public @interface YearOnOrAfter {

	/** Floor year, inclusive. */
	int value();

	/**
	 * Message for a value below the floor. The default is deliberately
	 * year-agnostic because {@link #value()} is a parameter — pass the
	 * field-specific copy at the call site.
	 */
	String message() default "La fecha no puede ser anterior al año mínimo.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** {@code null} passes: the DTO's {@code @NotNull} owns presence. */
	final class Validator implements ConstraintValidator<YearOnOrAfter, LocalDate> {

		private int minYear;

		@Override
		public void initialize(YearOnOrAfter annotation) {
			this.minYear = annotation.value();
		}

		@Override
		public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
			return value == null || value.getYear() >= minYear;
		}
	}
}
