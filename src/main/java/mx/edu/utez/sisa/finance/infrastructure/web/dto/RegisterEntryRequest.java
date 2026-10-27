package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentGatewayStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code POST /finance/payments/{id}/entries} body. The collector's id comes
 * from the JWT; {@code collectedByName} is optional. {@code isPhysical} marks
 * a ventanilla collection.
 */
public record RegisterEntryRequest(@NotNull @Positive BigDecimal amount, @NotNull PaymentEntryMethod method,
		@NotNull PaymentChannel channel, String gateway, String externalOrderId, UUID linkId, String reference,
		String receiptNumber, boolean isPhysical, String collectedByName, PaymentGatewayStatus gatewayStatus) {
}
