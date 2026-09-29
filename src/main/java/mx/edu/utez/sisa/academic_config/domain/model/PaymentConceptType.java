package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Classifies what a {@link PaymentConcept} charges for (source:
 * {@code 02-config-academica.md} — "Catálogo de conceptos de pago").
 *
 * <p>{@link #ADMISSION} is what the admission flow prices the ficha from: the
 * admission queries ({@code PaymentConceptQueryAdapter},
 * {@code AdmissionPaymentRepositoryAdapter},
 * {@code ProgramAdmissionConfigJpaRepository}) look up an {@code ACTIVE}
 * {@code ADMISSION} concept flagged {@code is_tuition}, narrowed to one
 * {@code Active} row per program. Before it existed they borrowed
 * {@link #ENROLLMENT}, which conflated "the admission fee" with "the semester
 * enrollment quota" and made the admission vocabulary unreachable from the
 * catalog UI.
 *
 * <p>{@link #ENROLLMENT} and {@link #REINSCRIPTION} remain for the semester
 * quotas; {@code isTuition} is the separate flag that marks the semester one.
 */
public enum PaymentConceptType {
	ADMISSION,
	ENROLLMENT,
	REINSCRIPTION,
	EXTRAORDINARY,
	DOCUMENT,
	OTHER
}
