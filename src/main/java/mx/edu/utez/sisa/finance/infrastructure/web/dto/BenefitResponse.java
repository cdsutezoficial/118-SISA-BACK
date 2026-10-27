package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Discount as shown in quote/payment/statement responses. {@code percent} /
 * {@code amount} are populated according to {@code mode}.
 */
public record BenefitResponse(UUID id, BenefitMode mode, BigDecimal percent, BigDecimal amount, String reason,
		String grantedByName) {
}
