package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.model.CreditStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ListOverpaymentsUseCase.OverpaymentView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OverpaymentResponse(UUID id, UUID candidateId, String personName, String curp, BigDecimal amount,
		CreditStatus status, String originReference, String appliedToReference, String externalOrderId,
		Instant createdAt) {

	public static OverpaymentResponse from(OverpaymentView view) {
		return new OverpaymentResponse(view.creditId(), view.candidateId(), view.personName(), view.curp(),
				view.amount(), view.status(), view.originReference(), view.appliedToReference(),
				view.externalOrderId(), view.createdAt());
	}
}
