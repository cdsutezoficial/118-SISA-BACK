package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * F1: what the cash desk took. It has to be the amount the preview showed; if the ficha
 * owes something else by now, nothing is booked (409 {@code ADMISSION_CASH_AMOUNT_CHANGED}).
 */
public record CashPaymentRequest(@NotNull @PositiveOrZero BigDecimal expectedAmount, Boolean useCredit) {

	/** Without the flag (older clients): only cash. */
	public boolean usesCredit() {
		return Boolean.TRUE.equals(useCredit);
	}
}
