package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;

/**
 * {@code POST /finance/payments/{id}/apply-credit} body: the surface that
 * applied the saldo a favor ({@code PORTAL} when the person does it, {@code
 * CASHIER} when SE/Finanzas does it).
 */
public record ApplyCreditRequest(@NotNull PaymentChannel channel) {
}
