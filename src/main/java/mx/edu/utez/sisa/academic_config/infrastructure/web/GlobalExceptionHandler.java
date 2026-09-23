package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicDivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicProgramNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DirectorNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionNameException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGradeScaleException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateLevelNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateOfferNameModalityException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePeriodException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePlanVersionException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateClassificationCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGenerationNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaNameException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentRateException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateSubjectCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GradeScaleNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidGradeScaleEntriesException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPeriodStatusTransitionException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentAreaDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentRateDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidSocialServiceLevelException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentAreaNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelHasSubjectsException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelInUseException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramAdmissionConfigException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidProgramAdmissionConfigDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.SubjectNotFoundException;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Maps {@code academic_config}'s 4 domain exceptions to HTTP status codes
 * (design.md — Decision: Own {@code @RestControllerAdvice}, additive only).
 * Generic handlers (bean validation, type-mismatch, catch-all) already exist
 * app-globally in {@code identity.GlobalExceptionHandler} and are
 * deliberately NOT redeclared here — a duplicate {@code @ExceptionHandler}
 * for the same exception type across two {@code @RestControllerAdvice} beans
 * is ambiguous.
 *
 * <p>Explicit {@link Component} bean name: both this class and
 * {@code identity.infrastructure.web.GlobalExceptionHandler} share the same
 * simple class name, which otherwise collide under Spring's default
 * annotation-derived bean naming (both would resolve to
 * {@code "globalExceptionHandler"}) — {@code @RestControllerAdvice} itself
 * has no name attribute, so an explicit {@code @Component} name is the
 * disambiguation.
 */
@RestControllerAdvice
@Component("academicConfigGlobalExceptionHandler")
public class GlobalExceptionHandler {

	@ExceptionHandler(AcademicDivisionNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(AcademicDivisionNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró la división académica solicitada.", request);
	}

	@ExceptionHandler({ DuplicateDivisionCodeException.class, DuplicateDivisionNameException.class })
	public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una división académica con la información proporcionada.", request);
	}

	@ExceptionHandler(DirectorNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleDirectorNotFound(DirectorNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "El director seleccionado no es válido para esta división.", request);
	}

	@ExceptionHandler(AcademicProgramNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleProgramNotFound(AcademicProgramNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró la carrera solicitada.", request);
	}

	@ExceptionHandler({ DuplicateProgramCodeException.class, DuplicateOfferNameModalityException.class })
	public ResponseEntity<ErrorResponse> handleProgramConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una carrera con la información proporcionada.", request);
	}

	@ExceptionHandler(DivisionNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleDivisionNotFoundForProgram(DivisionNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "La división académica seleccionada no existe.", request);
	}

	@ExceptionHandler({ AcademicPlanNotFoundException.class, PlanLevelNotFoundException.class,
			SubjectNotFoundException.class, GradeScaleNotFoundException.class })
	public ResponseEntity<ErrorResponse> handlePlanNotFound(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró el elemento solicitado del plan de estudios.", request);
	}

	@ExceptionHandler({ ProgramNotFoundException.class, InvalidSocialServiceLevelException.class,
			InvalidPlanDataException.class, InvalidGradeScaleEntriesException.class })
	public ResponseEntity<ErrorResponse> handlePlanBadRequest(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Revisa la información proporcionada para el plan de estudios.", request);
	}

	@ExceptionHandler({ DuplicatePlanVersionException.class, DuplicateLevelNumberException.class,
			DuplicateSubjectCodeException.class, PlanLevelHasSubjectsException.class,
			PlanLevelInUseException.class, DuplicateGradeScaleException.class })
	public ResponseEntity<ErrorResponse> handlePlanConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "No se puede completar la operación porque la información ya existe o está en uso.", request);
	}

	@ExceptionHandler(DuplicateClassificationCodeException.class)
	public ResponseEntity<ErrorResponse> handleClassificationConflict(DuplicateClassificationCodeException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una clasificación con la clave proporcionada.", request);
	}

	@ExceptionHandler(ClassificationNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleClassificationNotFound(ClassificationNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró la clasificación solicitada.", request);
	}

	@ExceptionHandler(AcademicPeriodNotFoundException.class)
	public ResponseEntity<ErrorResponse> handlePeriodNotFound(AcademicPeriodNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró el periodo académico solicitado.", request);
	}

	@ExceptionHandler(DuplicatePeriodException.class)
	public ResponseEntity<ErrorResponse> handlePeriodConflict(DuplicatePeriodException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe un periodo académico con la información proporcionada.", request);
	}

	@ExceptionHandler(InvalidPeriodStatusTransitionException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPeriodStatusTransition(InvalidPeriodStatusTransitionException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "No es posible realizar esta transición para el periodo académico.", request);
	}

	@ExceptionHandler(GenerationNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleGenerationNotFound(GenerationNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró la generación solicitada.", request);
	}

	@ExceptionHandler(DuplicateGenerationNumberException.class)
	public ResponseEntity<ErrorResponse> handleGenerationConflict(DuplicateGenerationNumberException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una generación con la información proporcionada.", request);
	}

	@ExceptionHandler({ PlanNotFoundException.class, PeriodNotFoundException.class })
	public ResponseEntity<ErrorResponse> handleGenerationBadRequest(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "El plan de estudios o periodo académico seleccionado no existe.", request);
	}

	@ExceptionHandler(GroupNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleGroupNotFound(GroupNotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró el grupo solicitado.", request);
	}

	@ExceptionHandler(GenerationReferenceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleGenerationReferenceNotFound(GenerationReferenceNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "La generación, periodo o nivel seleccionado no existe.", request);
	}

	@ExceptionHandler(PaymentConceptNotFoundException.class)
	public ResponseEntity<ErrorResponse> handlePaymentConceptNotFound(PaymentConceptNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró el concepto de pago solicitado.", request);
	}

	@ExceptionHandler(PaymentAreaNotFoundException.class)
	public ResponseEntity<ErrorResponse> handlePaymentAreaNotFound(PaymentAreaNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró el área de facturación solicitada.", request);
	}

	@ExceptionHandler({ DuplicatePaymentAreaCodeException.class, DuplicatePaymentAreaNameException.class })
	public ResponseEntity<ErrorResponse> handlePaymentAreaConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe un área de facturación con la información proporcionada.", request);
	}

	@ExceptionHandler(InvalidPaymentAreaDataException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPaymentAreaData(InvalidPaymentAreaDataException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Revisa la información proporcionada para el área de facturación.", request);
	}

	@ExceptionHandler(InvalidPaymentConceptDataException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPaymentConceptData(InvalidPaymentConceptDataException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Revisa la información proporcionada para el concepto de pago.", request);
	}

	@ExceptionHandler(PaymentConceptReferenceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handlePaymentConceptReferenceNotFound(
			PaymentConceptReferenceNotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "La referencia seleccionada para el concepto de pago no existe.", request);
	}

	@ExceptionHandler(InvalidPaymentRateDataException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPaymentRateData(InvalidPaymentRateDataException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Revisa la información proporcionada para la tarifa.", request);
	}

	@ExceptionHandler(DuplicatePaymentRateException.class)
	public ResponseEntity<ErrorResponse> handlePaymentRateConflict(DuplicatePaymentRateException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una tarifa vigente con la información proporcionada.", request);
	}

	@ExceptionHandler(ProgramAdmissionConfigNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleProgramAdmissionConfigNotFound(ProgramAdmissionConfigNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró la configuración de admisión solicitada.", request);
	}

	@ExceptionHandler(DuplicateProgramAdmissionConfigException.class)
	public ResponseEntity<ErrorResponse> handleProgramAdmissionConfigConflict(
			DuplicateProgramAdmissionConfigException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una configuración de admisión para la carrera y periodo seleccionados.", request);
	}

	@ExceptionHandler(InvalidProgramAdmissionConfigDataException.class)
	public ResponseEntity<ErrorResponse> handleInvalidProgramAdmissionConfigData(
			InvalidProgramAdmissionConfigDataException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Revisa la información proporcionada para la configuración de admisión.", request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), statusLabel(status), message,
				request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}

	private static String statusLabel(HttpStatus status) {
		return switch (status) {
			case BAD_REQUEST -> "Solicitud inválida";
			case NOT_FOUND -> "No encontrado";
			case CONFLICT -> "Conflicto";
			default -> "Error";
		};
	}
}
