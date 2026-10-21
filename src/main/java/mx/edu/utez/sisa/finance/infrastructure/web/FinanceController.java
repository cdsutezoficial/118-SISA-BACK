package mx.edu.utez.sisa.finance.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase.ApplyCreditCommand;
import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase.CreditApplicationResult;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentResult;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetPaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterExemptionUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterExemptionUseCase.RegisterExemptionCommand;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterPaymentEntryUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterPaymentEntryUseCase.RegisterEntryCommand;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository.PaymentSearchCriteria;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository.PaymentSearchPage;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.AccountStatementPaymentResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.AccountStatementResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.ApplyCreditRequest;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.CreatePaymentRequest;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.CreditApplicationResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.PaymentListItemResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.PaymentListResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.PaymentResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.RegisterEntryRequest;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.RegisterExemptionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Núcleo de cobro: {@code POST /finance/payments} (crear adeudo, {@code
 * PAYMENTS_CREATE}), {@code GET /finance/payments} (lista, {@code
 * PAYMENTS_READ}), {@code GET /finance/payments/{id}} (detalle con abonos,
 * {@code PAYMENTS_READ}), {@code POST /finance/payments/{id}/entries}
 * (registrar abono, {@code PAYMENTS_SETTLE}), and {@code GET
 * /finance/account-statement} (estado de cuenta). Role authorization is the
 * coarse {@code ADMIN}/{@code PERSONAL_FINANZAS} pair in
 * {@code identity.SecurityFilterConfig}; the fine-grained {@code PAYMENTS_*}
 * layer lives in {@code PermissionRegistry}.
 */
@RestController
@RequestMapping("/finance")
public class FinanceController {

	private final CreatePaymentUseCase createPaymentUseCase;

	private final RegisterPaymentEntryUseCase registerPaymentEntryUseCase;

	private final GetPaymentUseCase getPaymentUseCase;

	private final GetAccountStatementUseCase getAccountStatementUseCase;

	private final RegisterExemptionUseCase registerExemptionUseCase;

	private final ApplyCreditToPaymentUseCase applyCreditToPaymentUseCase;

	private final PaymentRepository paymentRepository;

	public FinanceController(CreatePaymentUseCase createPaymentUseCase,
			RegisterPaymentEntryUseCase registerPaymentEntryUseCase, GetPaymentUseCase getPaymentUseCase,
			GetAccountStatementUseCase getAccountStatementUseCase, RegisterExemptionUseCase registerExemptionUseCase,
			ApplyCreditToPaymentUseCase applyCreditToPaymentUseCase, PaymentRepository paymentRepository) {
		this.createPaymentUseCase = createPaymentUseCase;
		this.registerPaymentEntryUseCase = registerPaymentEntryUseCase;
		this.getPaymentUseCase = getPaymentUseCase;
		this.getAccountStatementUseCase = getAccountStatementUseCase;
		this.registerExemptionUseCase = registerExemptionUseCase;
		this.applyCreditToPaymentUseCase = applyCreditToPaymentUseCase;
		this.paymentRepository = paymentRepository;
	}

	@PostMapping("/payments")
	public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
		PaymentResult result = createPaymentUseCase.createPayment(new CreatePaymentCommand(request.candidateId(),
				request.studentId(), request.conceptCode(), request.periodId(), request.generationId(),
				request.programId(), request.level(), request.origin(), request.dueAt(), request.onDate(),
				currentUserId()));
		return ResponseEntity.status(HttpStatus.CREATED).body(FinanceResponseMapper.toPayment(result));
	}

	@PostMapping("/payments/{id}/entries")
	public ResponseEntity<PaymentResponse> registerEntry(@PathVariable UUID id,
			@Valid @RequestBody RegisterEntryRequest request) {
		PaymentResult result = registerPaymentEntryUseCase.registerEntry(new RegisterEntryCommand(id,
				request.amount(), request.method(), request.channel(), request.gateway(), request.externalOrderId(),
				request.linkId(), request.reference(), request.receiptNumber(), request.isPhysical(), currentUserId(),
				request.collectedByName(), request.gatewayStatus()));
		return ResponseEntity.ok(FinanceResponseMapper.toPayment(result));
	}

	/**
	 * Registers the {@code $0} exemption entry (R4) on an already-{@code EXEMPT}
	 * adeudo — the receipt rastro of a 100 % discount. {@code BENEFITS_CREATE}.
	 */
	@PostMapping("/payments/{id}/exempt")
	public ResponseEntity<PaymentResponse> registerExemption(@PathVariable UUID id,
			@Valid @RequestBody RegisterExemptionRequest request) {
		PaymentResult result = registerExemptionUseCase.registerExemption(new RegisterExemptionCommand(id,
				request.receiptNumber(), request.reference(), currentUserId(), request.collectedByName()));
		return ResponseEntity.ok(FinanceResponseMapper.toPayment(result));
	}

	/**
	 * Applies the person's available saldo a favor to this adeudo (R12).
	 * Invocable by the owner or staff; no supervisor approval.
	 * {@code PAYMENTS_SETTLE}.
	 */
	@PostMapping("/payments/{id}/apply-credit")
	public ResponseEntity<CreditApplicationResponse> applyCredit(@PathVariable UUID id,
			@Valid @RequestBody ApplyCreditRequest request) {
		CreditApplicationResult result = applyCreditToPaymentUseCase
				.applyCredit(new ApplyCreditCommand(id, request.channel(), currentUserId()));
		return ResponseEntity.ok(FinanceResponseMapper.toCreditApplication(result));
	}

	@GetMapping("/payments/{id}")
	public ResponseEntity<AccountStatementPaymentResponse> getPayment(@PathVariable UUID id) {
		return ResponseEntity.ok(FinanceResponseMapper.toStatementPayment(getPaymentUseCase.getPayment(id)));
	}

	@GetMapping("/payments")
	public ResponseEntity<PaymentListResponse> listPayments(@RequestParam(required = false) UUID candidateId,
			@RequestParam(required = false) UUID studentId, @RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) String conceptCode, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		PaymentSearchPage result = paymentRepository
				.search(new PaymentSearchCriteria(candidateId, studentId, status, conceptCode, page, size));
		List<PaymentListItemResponse> items = result.content().stream().map(FinanceResponseMapper::toListItem).toList();
		return ResponseEntity.ok(
				new PaymentListResponse(items, result.totalElements(), result.totalPages(), page, size));
	}

	@GetMapping("/account-statement")
	public ResponseEntity<AccountStatementResponse> accountStatement(@RequestParam(required = false) UUID candidateId,
			@RequestParam(required = false) UUID studentId) {
		return ResponseEntity.ok(FinanceResponseMapper
				.toStatement(getAccountStatementUseCase.getAccountStatement(candidateId, studentId)));
	}

	/**
	 * Extracts the acting user's id from the JWT principal — same mechanism as
	 * {@code PaymentAreaController#currentUserId}. Used for the abono's
	 * collector and the adeudo's creator.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}
}
