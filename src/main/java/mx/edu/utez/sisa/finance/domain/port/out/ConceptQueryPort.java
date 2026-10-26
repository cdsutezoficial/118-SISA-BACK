package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only out-port over {@code PaymentConcept} (a bounded context NOT owned
 * by {@code finance} — the catalog lives in {@code academic_config}). Same
 * cross-context pattern as {@code admission.PaymentConceptQueryPort}.
 *
 * <p>{@code finance} resolves a concept by its stable {@code code} (the
 * free-text charge key) and needs only what quoting requires: does it exist,
 * is it ACTIVE, is today inside its window, and what type is it.
 */
public interface ConceptQueryPort {

	/**
	 * The concept with the given {@code code} (case-insensitive), restricted
	 * to ACTIVE rows whose availability window (when set) contains
	 * {@code onDate}. Empty means "no hay concepto a la venta hoy" — the
	 * caller distinguishes "no existe" from "no está a la venta" through
	 * {@link #findByCode(String)}, which ignores the window.
	 */
	Optional<ConceptView> findSellableByCode(String code, LocalDate onDate);

	/**
	 * The concept with the given {@code code} among ACTIVE rows, window NOT
	 * applied. Exists purely to tell the two failures apart — same rationale
	 * as {@code admission.PaymentConceptQueryPort#findActiveEnrollmentForProgram(UUID)}:
	 * a catalog misconfiguration and the normal end of a sales period deserve
	 * different messages.
	 */
	Optional<ConceptView> findByCode(String code);

	/**
	 * Minimal projection quoting needs. {@code cost} is deliberately absent:
	 * the price is whatever rate applies to the program, so it is asked of
	 * {@link ConceptRateQueryPort}, never read off the concept.
	 */
	record ConceptView(UUID id, String name, String code, PaymentConceptType type) {
	}
}
