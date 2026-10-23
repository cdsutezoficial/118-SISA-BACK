package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read projection of a {@code PaymentBenefit} shared by the whole Fase 2 CRUD
 * (create/update/cancel/list/report) — one shape, so every endpoint renders a
 * discount the same way. {@code inForce} is {@code true} when the discount is
 * {@code ACTIVE} and today (business date) sits inside its window — the
 * "vigente/vencido" flag the list screen shows.
 */
public record BenefitView(UUID id, UUID candidateId, UUID studentId, String conceptCode, UUID periodId,
		UUID generationId, BenefitMode mode, BigDecimal percent, BigDecimal amount, LocalDate validFrom,
		LocalDate validTo, BenefitStatus status, String reason, UUID grantedById, String grantedByName,
		Instant createdAt, boolean inForce, UUID paymentId) {
}
