package mx.edu.utez.sisa.finance.infrastructure.web;

import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.QuoteCommand;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.QuoteResult;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.QuoteResponse;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cotizador endpoint ({@code GET /finance/quote}, permiso {@code
 * PAYMENTS_QUOTE} — R3). Read-only; the adeudo is created through
 * {@code POST /finance/payments}.
 */
@RestController
@RequestMapping("/finance")
public class QuoteController {

	private final QuotePaymentUseCase quotePaymentUseCase;

	public QuoteController(QuotePaymentUseCase quotePaymentUseCase) {
		this.quotePaymentUseCase = quotePaymentUseCase;
	}

	@GetMapping("/quote")
	public QuoteResponse quote(@RequestParam(required = false) UUID candidateId,
			@RequestParam(required = false) UUID studentId, @RequestParam String conceptCode,
			@RequestParam(required = false) UUID periodId, @RequestParam(required = false) UUID generationId,
			@RequestParam UUID programId, @RequestParam(required = false) AcademicLevel level,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {
		QuoteResult result = quotePaymentUseCase.quote(
				new QuoteCommand(candidateId, studentId, conceptCode, periodId, generationId, programId, level, onDate));
		return new QuoteResponse(result.conceptType(), result.originalAmount(), result.discountAmount(),
				result.amountDue(), FinanceResponseMapper.toBenefit(result.benefit()), result.exempt());
	}
}
