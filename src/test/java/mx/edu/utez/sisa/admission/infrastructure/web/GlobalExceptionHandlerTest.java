package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyExistsException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigSalesClosedException;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the {@code code} each admission failure carries.
 *
 * <p>The codes are the HTTP contract: the registration wizard branches on them
 * to decide where to send an applicant, and it branches on them instead of the
 * message because three different 409s share one status and the messages are
 * applicant-facing copy. That makes these assertions deliberately literal — if a
 * constant is renamed, the frontend silently stops recognising the error and
 * falls back to the generic "you already have a candidate with that CURP",
 * which is the exact failure this work removed.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

	@Mock
	private HttpServletRequest request;

	private GlobalExceptionHandler handler;

	@BeforeEach
	void setUp() {
		handler = new GlobalExceptionHandler();
		when(request.getRequestURI()).thenReturn("/candidates");
	}

	@Test
	@DisplayName("quota exhausted carries ADMISSION_QUOTA_REACHED and keeps its applicant-facing message")
	void quotaExhaustedIsDistinguishable() {
		String message = "La carrera agotó su cupo de 15 fichas.";

		ResponseEntity<ErrorResponse> response = handler.handleProgramAdmissionConfigCapacityReached(
				new ProgramAdmissionConfigCapacityReachedException(message), request);

		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo(GlobalExceptionHandler.CODE_QUOTA_REACHED);
		assertThat(response.getBody().message()).isEqualTo(message);
	}

	@Test
	@DisplayName("the three 409s the wizard must tell apart have three different codes")
	void theThreeConflictsDoNotShareACode() {
		String soldOut = handler.handleProgramAdmissionConfigCapacityReached(
				new ProgramAdmissionConfigCapacityReachedException("cupo"), request).getBody().code();
		String closed = handler.handleProgramAdmissionConfigSalesClosed(
				new ProgramAdmissionConfigSalesClosedException("cerró"), request).getBody().code();
		String duplicate = handler.handleCandidateAlreadyExists(
				new CandidateAlreadyExistsException("ya existe"), request).getBody().code();
		String windowClosed = handler.handlePaymentConceptExpired(
				new PaymentConceptExpiredException("concepto"), request).getBody().code();

		assertThat(soldOut).isNotEqualTo(closed);
		assertThat(closed).isNotEqualTo(duplicate);
		assertThat(duplicate).isNotEqualTo(windowClosed);
		assertThat(soldOut).isEqualTo(GlobalExceptionHandler.CODE_QUOTA_REACHED);
		assertThat(closed).isEqualTo(GlobalExceptionHandler.CODE_SALES_WINDOW_CLOSED);
		assertThat(duplicate).isEqualTo(GlobalExceptionHandler.CODE_CANDIDATE_ALREADY_EXISTS);
		assertThat(windowClosed).isEqualTo(GlobalExceptionHandler.CODE_PAYMENT_WINDOW_CLOSED);
	}

	@Test
	@DisplayName("handlers without a code send null so the frontend falls back to the message")
	void handlersWithoutACodeSendNull() {
		ResponseEntity<ErrorResponse> response = handler.handleHighSchoolTypeNotFound(
				new mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException("no existe"), request);

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isNull();
		assertThat(response.getBody().message()).isEqualTo("no existe");
	}
}
