package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Classifies what a {@link PaymentConcept} charges for (source:
 * {@code 02-config-academica.md} lines 234-252 — "Catálogo de conceptos de
 * pago"). Five values exactly as documented — the frontend mock
 * ({@code ConceptosForm.tsx}) currently only offers 2 invented values
 * ("Recurrente"/"Una vez"); that mismatch is out of scope for this backend
 * phase and is tracked for Fase 3 (frontend wiring).
 */
public enum PaymentConceptType {
	ENROLLMENT,
	REINSCRIPTION,
	EXTRAORDINARY,
	DOCUMENT,
	OTHER
}
