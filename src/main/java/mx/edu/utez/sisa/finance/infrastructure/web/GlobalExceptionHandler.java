package mx.edu.utez.sisa.finance.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotEditableException;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.ConceptUnavailableException;
import mx.edu.utez.sisa.finance.shared.exception.CreditNotApplicableException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidBenefitDataException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidEntryAmountException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Maps {@code finance}'s domain exceptions to HTTP status codes (design.md —
 * own {@code @RestControllerAdvice}, additive only). Generic handlers (bean
 * validation, type-mismatch, catch-all) stay in
 * {@code identity.GlobalExceptionHandler} and are NOT redeclared here.
 *
 * <p>Every handler carries a stable {@code code} alongside the message so the
 * frontend branches on the code, never on reworded copy.
 *
 * <p>Explicit {@link Component} bean name: three modules now share the simple
 * class name {@code GlobalExceptionHandler}, which would otherwise collide
 * under Spring's default annotation-derived naming.
 */
@RestControllerAdvice
@Component("financeGlobalExceptionHandler")
public class GlobalExceptionHandler {

	public static final String CODE_PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
	public static final String CODE_CONCEPT_NOT_FOUND = "CONCEPT_NOT_FOUND";
	public static final String CODE_PERSON_NOT_FOUND = "PERSON_NOT_FOUND";
	public static final String CODE_CONCEPT_UNAVAILABLE = "CONCEPT_UNAVAILABLE";
	public static final String CODE_NO_RATE_AVAILABLE = "NO_RATE_AVAILABLE";
	public static final String CODE_INVALID_PAYMENT_DATA = "INVALID_PAYMENT_DATA";
	public static final String CODE_INVALID_ENTRY_AMOUNT = "INVALID_ENTRY_AMOUNT";
	public static final String CODE_PAYMENT_NOT_PAYABLE = "PAYMENT_NOT_PAYABLE";
	public static final String CODE_BENEFIT_NOT_FOUND = "BENEFIT_NOT_FOUND";
	public static final String CODE_INVALID_BENEFIT_DATA = "INVALID_BENEFIT_DATA";
	public static final String CODE_BENEFIT_NOT_EDITABLE = "BENEFIT_NOT_EDITABLE";
	public static final String CODE_CREDIT_NOT_APPLICABLE = "CREDIT_NOT_APPLICABLE";

	@ExceptionHandler(PaymentNotFoundException.class)
	public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, CODE_PAYMENT_NOT_FOUND, "No se encontró el adeudo solicitado.", request);
	}

	@ExceptionHandler(ConceptNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleConceptNotFound(ConceptNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, CODE_CONCEPT_NOT_FOUND, "No se encontró el concepto de pago indicado.",
				request);
	}

	@ExceptionHandler(PersonNotFoundException.class)
	public ResponseEntity<ErrorResponse> handlePersonNotFound(PersonNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, CODE_PERSON_NOT_FOUND, "No se encontró la persona indicada.", request);
	}

	@ExceptionHandler(ConceptUnavailableException.class)
	public ResponseEntity<ErrorResponse> handleConceptUnavailable(ConceptUnavailableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_CONCEPT_UNAVAILABLE,
				"El concepto no está disponible en la fecha indicada.", request);
	}

	@ExceptionHandler(NoRateAvailableException.class)
	public ResponseEntity<ErrorResponse> handleNoRateAvailable(NoRateAvailableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_NO_RATE_AVAILABLE,
				"No hay una tarifa vigente para el concepto y la carrera indicados.", request);
	}

	@ExceptionHandler(InvalidPaymentDataException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPaymentData(InvalidPaymentDataException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, CODE_INVALID_PAYMENT_DATA, "Revisa la información del adeudo.", request);
	}

	@ExceptionHandler(InvalidEntryAmountException.class)
	public ResponseEntity<ErrorResponse> handleInvalidEntryAmount(InvalidEntryAmountException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, CODE_INVALID_ENTRY_AMOUNT, "El monto del abono no es válido.", request);
	}

	@ExceptionHandler(PaymentNotPayableException.class)
	public ResponseEntity<ErrorResponse> handlePaymentNotPayable(PaymentNotPayableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_PAYMENT_NOT_PAYABLE,
				"El adeudo no acepta abonos en su estado actual o el monto excede el saldo.", request);
	}

	@ExceptionHandler(BenefitNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleBenefitNotFound(BenefitNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, CODE_BENEFIT_NOT_FOUND, "No se encontró el descuento solicitado.", request);
	}

	@ExceptionHandler(InvalidBenefitDataException.class)
	public ResponseEntity<ErrorResponse> handleInvalidBenefitData(InvalidBenefitDataException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, CODE_INVALID_BENEFIT_DATA, "Revisa la información del descuento.",
				request);
	}

	@ExceptionHandler(BenefitNotEditableException.class)
	public ResponseEntity<ErrorResponse> handleBenefitNotEditable(BenefitNotEditableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_BENEFIT_NOT_EDITABLE,
				"El descuento no se puede editar ni cancelar en su estado actual.", request);
	}

	@ExceptionHandler(CreditNotApplicableException.class)
	public ResponseEntity<ErrorResponse> handleCreditNotApplicable(CreditNotApplicableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, CODE_CREDIT_NOT_APPLICABLE,
				"No se puede aplicar el saldo a favor a este adeudo.", request);
	}

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
