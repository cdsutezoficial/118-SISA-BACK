package mx.edu.utez.sisa.finance.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.CancelBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreateBenefitUseCase.CreateBenefitCommand;
import mx.edu.utez.sisa.finance.domain.port.in.GetDiscountReportUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.ListBenefitsUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.ListBenefitsUseCase.BenefitFilter;
import mx.edu.utez.sisa.finance.domain.port.in.UpdateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.UpdateBenefitUseCase.UpdateBenefitCommand;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.BenefitDetailResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.BenefitListResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.CreateBenefitRequest;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.CreditListResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.CreditResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.DiscountReportResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.UpdateBenefitRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Descuentos (Fase 2): the CRUD the SE/Finanzas manager drives, plus the
 * saldo-a-favor listing and the discount report. Role authorization is the
 * coarse matcher in {@code identity.SecurityFilterConfig}; the fine-grained
 * {@code BENEFITS_*}/{@code PAYMENTS_READ} layer lives in
 * {@code PermissionRegistry}.
 */
@RestController
@RequestMapping("/finance")
public class BenefitController {

	private final CreateBenefitUseCase createBenefitUseCase;

	private final UpdateBenefitUseCase updateBenefitUseCase;

	private final CancelBenefitUseCase cancelBenefitUseCase;

	private final ListBenefitsUseCase listBenefitsUseCase;

	private final GetDiscountReportUseCase getDiscountReportUseCase;

	private final PaymentCreditRepository paymentCreditRepository;

	public BenefitController(CreateBenefitUseCase createBenefitUseCase, UpdateBenefitUseCase updateBenefitUseCase,
			CancelBenefitUseCase cancelBenefitUseCase, ListBenefitsUseCase listBenefitsUseCase,
			GetDiscountReportUseCase getDiscountReportUseCase, PaymentCreditRepository paymentCreditRepository) {
		this.createBenefitUseCase = createBenefitUseCase;
		this.updateBenefitUseCase = updateBenefitUseCase;
		this.cancelBenefitUseCase = cancelBenefitUseCase;
		this.listBenefitsUseCase = listBenefitsUseCase;
		this.getDiscountReportUseCase = getDiscountReportUseCase;
		this.paymentCreditRepository = paymentCreditRepository;
	}

	@GetMapping("/benefits")
	public ResponseEntity<BenefitListResponse> list(@RequestParam(required = false) UUID candidateId,
			@RequestParam(required = false) UUID studentId, @RequestParam(required = false) String conceptCode,
			@RequestParam(required = false) BenefitStatus status) {
		List<BenefitDetailResponse> items = listBenefitsUseCase
				.listBenefits(new BenefitFilter(candidateId, studentId, conceptCode, status)).stream()
				.map(FinanceResponseMapper::toBenefitDetail).toList();
		return ResponseEntity.ok(new BenefitListResponse(items, items.size()));
	}

	@PostMapping("/benefits")
	public ResponseEntity<BenefitDetailResponse> create(@Valid @RequestBody CreateBenefitRequest request) {
		BenefitView view = createBenefitUseCase.createBenefit(new CreateBenefitCommand(request.candidateId(),
				request.studentId(), request.conceptCode(), request.periodId(), request.generationId(), request.mode(),
				request.percent(), request.amount(), request.validFrom(), request.validTo(), request.reason(),
				currentUserId(), request.grantedByName()));
		return ResponseEntity.status(HttpStatus.CREATED).body(FinanceResponseMapper.toBenefitDetail(view));
	}

	@PutMapping("/benefits/{id}")
	public ResponseEntity<BenefitDetailResponse> update(@PathVariable UUID id,
			@Valid @RequestBody UpdateBenefitRequest request) {
		BenefitView view = updateBenefitUseCase.updateBenefit(new UpdateBenefitCommand(id, request.periodId(),
				request.generationId(), request.mode(), request.percent(), request.amount(), request.validFrom(),
				request.validTo(), request.reason()));
		return ResponseEntity.ok(FinanceResponseMapper.toBenefitDetail(view));
	}

	@PatchMapping("/benefits/{id}/cancel")
	public ResponseEntity<BenefitDetailResponse> cancel(@PathVariable UUID id) {
		return ResponseEntity.ok(FinanceResponseMapper.toBenefitDetail(cancelBenefitUseCase.cancelBenefit(id)));
	}

	@GetMapping("/credits")
	public ResponseEntity<CreditListResponse> listCredits(@RequestParam(required = false) UUID candidateId,
			@RequestParam(required = false) UUID studentId) {
		List<CreditResponse> items = (candidateId != null ? paymentCreditRepository.findByCandidateId(candidateId)
				: paymentCreditRepository.findByStudentId(studentId)).stream()
				.map(FinanceResponseMapper::toCredit).toList();
		return ResponseEntity.ok(new CreditListResponse(items, items.size()));
	}

	@GetMapping("/discounts/report")
	public ResponseEntity<DiscountReportResponse> report(@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		return ResponseEntity.ok(FinanceResponseMapper.toReport(getDiscountReportUseCase.getReport(from, to)));
	}

	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}
}
