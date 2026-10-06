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
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGroupCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaNameException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentConceptCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentQuotaLevelException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentRateException;
import mx.edu.utez.sisa.academic_config.shared.exception.IncompletePaymentRateSetException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateSubjectCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GradeScaleNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupCodeLevelMismatchException;
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
import mx.edu.utez.sisa.academic_config.shared.exception.NotEnoughGroupCodesException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * Catálogo de áreas de pago: el nombre normalizado ya existe en otro área
	 * (Fase 11).
	 *
	 * <p>Es un código aparte y no uno genérico de 409 porque {@code PaymentArea}
	 * tiene <b>dos</b> claves de negocio únicas, y el frontend tiene que poder
	 * pegarle el error al campo que corresponde sin adivinar. Con el handler
	 * único que había antes ("Ya existe un área de facturación con la
	 * información proporcionada."), {@code AreasForm} no tenía forma de saber si
	 * el choque había sido por el nombre o por la clave, y lo pintaba como un
	 * banner genérico; el usuario tenía que deducir por copy cuál de los dos
	 * campos corregir.
	 */
	public static final String CODE_PAYMENT_AREA_NAME_DUPLICATE = "PAYMENT_AREA_NAME_DUPLICATE";
	/**
	 * Espejo de {@link #CODE_PAYMENT_AREA_NAME_DUPLICATE} para la clave del área.
	 * Misma razón para no reusar aquel: son dos campos distintos del formulario y
	 * el copy de cada uno habla de su cosa.
	 */
	public static final String CODE_PAYMENT_AREA_CODE_DUPLICATE = "PAYMENT_AREA_CODE_DUPLICATE";

	/**
	 * La clave del grupo no corresponde al nivel elegido (C1 de las incidencias
	 * de la primera prueba manual). Va con código porque es el único 400 de
	 * {@code POST/PUT /groups} que pertenece al campo {@code code}: los demás
	 * 400 son campos ausentes o el binding del patrón, y el frontend ya los
	 * resuelve en el form. Con un copy fijo y un código estable, {@code
	 * GruposForm} pega el mensaje en {@code code} sin adivinar por texto.
	 */
	public static final String CODE_GROUP_CODE_LEVEL_MISMATCH = "GROUP_CODE_LEVEL_MISMATCH";

	@ExceptionHandler(AcademicDivisionNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(AcademicDivisionNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró la división académica solicitada.", request);
	}

	@ExceptionHandler(DuplicateDivisionNameException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateDivisionName(DuplicateDivisionNameException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "El nombre de la división ya está en uso.", request);
	}

	@ExceptionHandler(DuplicateDivisionCodeException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateDivisionCode(DuplicateDivisionCodeException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "La clave de la división ya está en uso.", request);
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

	@ExceptionHandler(DuplicateProgramCodeException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateProgramCode(DuplicateProgramCodeException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "La clave de la carrera ya está en uso.", request);
	}

	@ExceptionHandler(DuplicateOfferNameModalityException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateOfferNameModality(DuplicateOfferNameModalityException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una carrera con el mismo nombre de oferta y modalidad.", request);
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

	@ExceptionHandler({ ProgramNotFoundException.class, InvalidSocialServiceLevelException.class })
	public ResponseEntity<ErrorResponse> handlePlanBadRequest(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Revisa la información proporcionada para el plan de estudios.", request);
	}

	@ExceptionHandler({ InvalidPlanDataException.class, InvalidGradeScaleEntriesException.class })
	public ResponseEntity<ErrorResponse> handlePlanDataBadRequest(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(DuplicatePlanVersionException.class)
	public ResponseEntity<ErrorResponse> handleDuplicatePlanVersion(DuplicatePlanVersionException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe un plan de estudios con esa versión para esta carrera.", request);
	}

	@ExceptionHandler(DuplicateLevelNumberException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateLevelNumber(DuplicateLevelNumberException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe un nivel con ese número en este plan de estudios.", request);
	}

	@ExceptionHandler(DuplicateSubjectCodeException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateSubjectCode(DuplicateSubjectCodeException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una materia con ese código en este plan de estudios.", request);
	}

	@ExceptionHandler(DuplicateGradeScaleException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateGradeScale(DuplicateGradeScaleException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe un rango de calificación con esa clasificación en este plan.", request);
	}

	@ExceptionHandler(PlanLevelHasSubjectsException.class)
	public ResponseEntity<ErrorResponse> handlePlanLevelHasSubjects(PlanLevelHasSubjectsException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "No se puede eliminar el nivel porque tiene materias asignadas.", request);
	}

	@ExceptionHandler(PlanLevelInUseException.class)
	public ResponseEntity<ErrorResponse> handlePlanLevelInUse(PlanLevelInUseException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "No se puede eliminar el nivel porque está en uso.", request);
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

	/**
	 * 409 de grupos. El copy no menciona ningún campo a propósito: el frontend
	 * lo atribuye a {@code code} sin mirar el texto (el 409 de
	 * {@code POST/PUT /groups} sólo puede ser el duplicado de
	 * {@code (generationId, code)}), y el mismo copy sirve para el 409 de la
	 * creación masiva, donde el conflicto puede venir de otra transacción que
	 * tomó las mismas letras.
	 */
	@ExceptionHandler(DuplicateGroupCodeException.class)
	public ResponseEntity<ErrorResponse> handleGroupConflict(DuplicateGroupCodeException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe un grupo con esa clave en la generación.", request);
	}

	/**
	 * 400 cuando la clave del grupo no describe al nivel seleccionado (ej.
	 * Nivel 3 con {@code 5A}). El copy trae el ejemplo a propósito: sin él, el
	 * usuario ve una regla y no sabe qué escribir en su lugar.
	 */
	@ExceptionHandler(GroupCodeLevelMismatchException.class)
	public ResponseEntity<ErrorResponse> handleGroupCodeLevelMismatch(GroupCodeLevelMismatchException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, CODE_GROUP_CODE_LEVEL_MISMATCH,
				"La clave del grupo debe corresponder al nivel seleccionado (ej. 3A para el Nivel 3).", request);
	}

	/**
	 * 409 de la creacion masiva de grupos: no quedan letras libres en el rango
	 * A-Z para esa cantidad, o bien otra transaccion se llevo las letras entre
	 * la lectura y la escritura. Mismo status que
	 * {@code DuplicateGroupCodeException} y copy distinto a proposito: uno es
	 * "ya existe un grupo con esa clave" y este "no cabe la cantidad que pediste".
	 */
	@ExceptionHandler(NotEnoughGroupCodesException.class)
	public ResponseEntity<ErrorResponse> handleNotEnoughGroupCodes(NotEnoughGroupCodesException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT,
				"No hay suficientes claves de grupo libres en ese nivel para crear la cantidad solicitada.", request);
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

	/**
	 * Área de pago duplicada por nombre normalizado (Fase 11). El mensaje de la
	 * excepción ({@code "Payment area name already in use: ..."}) va al log y no
	 * al cuerpo de la respuesta: es texto para el desarrollador, y el catálogo
	 * necesita copy en español.
	 */
	@ExceptionHandler(DuplicatePaymentAreaNameException.class)
	public ResponseEntity<ErrorResponse> handleDuplicatePaymentAreaName(DuplicatePaymentAreaNameException ex,
			HttpServletRequest request) {
		log.warn("Duplicate payment area name rejected on {}: {}", request.getRequestURI(), ex.getMessage());
		return build(HttpStatus.CONFLICT, CODE_PAYMENT_AREA_NAME_DUPLICATE,
				"El nombre del área ya está en uso.", request);
	}

	/**
	 * Área de pago duplicada por clave normalizada (Fase 11). Separate handler —
	 * not the same {@code @ExceptionHandler} with an array of exceptions as it was
	 * before — precisely because the frontend branches on the code to attach the
	 * message to the {@code code} field instead of the {@code name} one.
	 */
	@ExceptionHandler(DuplicatePaymentAreaCodeException.class)
	public ResponseEntity<ErrorResponse> handleDuplicatePaymentAreaCode(DuplicatePaymentAreaCodeException ex,
			HttpServletRequest request) {
		log.warn("Duplicate payment area code rejected on {}: {}", request.getRequestURI(), ex.getMessage());
		return build(HttpStatus.CONFLICT, CODE_PAYMENT_AREA_CODE_DUPLICATE,
				"La clave del área ya está en uso.", request);
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

	@ExceptionHandler(DuplicatePaymentConceptCodeException.class)
	public ResponseEntity<ErrorResponse> handleDuplicatePaymentConceptCode(DuplicatePaymentConceptCodeException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe un concepto de pago con ese código.", request);
	}

	/**
	 * 409 rather than 400 on purpose: the request itself is well-formed, it just
	 * collides with a rule that spans records — "one active recurring quota per
	 * level". 400 would tell the user to fix their input, and there is nothing in
	 * the input to fix.
	 */
	@ExceptionHandler(DuplicatePaymentQuotaLevelException.class)
	public ResponseEntity<ErrorResponse> handleDuplicatePaymentQuotaLevel(DuplicatePaymentQuotaLevelException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe una cuota periódica activa para ese nivel.", request);
	}

	/**
	 * Also 409 rather than 400, for the same reason as the level collision: the
	 * payload parsed and validated, and the refusal is about catalog state. The
	 * message is deliberately about the rule rather than echoing the missing
	 * program names, because the fix is "open the quota editor, which reloads the
	 * active careers", not "retype the list".
	 */
	@ExceptionHandler(IncompletePaymentRateSetException.class)
	public ResponseEntity<ErrorResponse> handleIncompletePaymentRateSet(IncompletePaymentRateSetException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Una cuota periódica debe tener tarifa para todas las carreras activas.",
				request);
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
		return build(status, null, message, request);
	}

	/**
	 * Builds the error body with a stable {@code code} alongside the
	 * human-readable {@code message} (Fase 11).
	 *
	 * <p>Why both: {@code PaymentArea} has two unique business keys, and the form
	 * has to tell "the name collides" from "the code collides" — two different
	 * fields to highlight. Both arrive as 409, and the messages get reworded by
	 * copy edits, so branching on the message is a trap. The code is the
	 * contract; the message is for the user.
	 */
	private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
			HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), statusLabel(status), message,
				request.getRequestURI(), code);
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
