package mx.edu.utez.sisa.finance.infrastructure.web.dto;

/**
 * {@code POST /finance/payments/{id}/exempt} body: the receipt data attached
 * to the {@code $0} exemption entry (R4). All fields optional.
 */
public record RegisterExemptionRequest(String receiptNumber, String reference, String collectedByName) {
}
