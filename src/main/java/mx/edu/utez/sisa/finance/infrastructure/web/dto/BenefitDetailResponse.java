package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A full discount as rendered by the Fase 2 CRUD. {@code inForce} is the
 * "vigente/vencido" flag computed against today's business date.
 */
public record BenefitDetailResponse(UUID id, UUID candidateId, UUID studentId, String conceptCode, UUID periodId,
		UUID generationId, BenefitMode mode, BigDecimal percent, BigDecimal amount, LocalDate validFrom,
		LocalDate validTo, BenefitStatus status, String reason, UUID grantedById, String grantedByName,
		Instant createdAt, boolean inForce, UUID paymentId) {
}
