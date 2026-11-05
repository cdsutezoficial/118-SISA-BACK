package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.admission.domain.port.in.CollectCashPaymentUseCase;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CashPaymentRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.PaymentConfirmationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * F1: cobro en efectivo en caja. Staff only ({@code CAJA}, {@code PERSONAL_FINANZAS},
 * {@code ADMIN}) with {@code PAYMENT_WINDOW_CONFIRM}; it shares no route with the
 * public online checkout under {@code /candidates/*}/payments.
 *
 * <p>{@code GET} answers how much to take, or refuses with the same 409 codes as the
 * checkout (window, place, payment in progress, already paid), before the money is
 * received. {@code POST} books it as {@code CASH} with who collected it, and returns the
 * receipt. The ficha PDF ({@code GET /candidates/{id}/ficha.pdf}) reprints it.
 */
@RestController
@RequestMapping("/candidates/{id}/cash-payment")
public class CashPaymentController {

	private final CollectCashPaymentUseCase collectCashPaymentUseCase;

	public CashPaymentController(CollectCashPaymentUseCase collectCashPaymentUseCase) {
		this.collectCashPaymentUseCase = collectCashPaymentUseCase;
	}

	@GetMapping
	public ResponseEntity<CollectCashPaymentUseCase.CashPaymentPreview> preview(@PathVariable UUID id) {
		return ResponseEntity.ok(collectCashPaymentUseCase.preview(id));
	}

	@PostMapping
	public ResponseEntity<PaymentConfirmationResponse> collect(@PathVariable UUID id,
			@Valid @RequestBody CashPaymentRequest request, Authentication authentication) {
		return ResponseEntity.ok(PaymentConfirmationResponse.from(collectCashPaymentUseCase.collect(id,
				request.expectedAmount(), UUID.fromString(authentication.getName()))));
	}
}
