package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Classifies what a {@link PaymentConcept} charges for (source:
 * {@code 02-config-academica.md} — "Catálogo de conceptos de pago").
 *
 * <p>{@link #ADMISSION} is what the admission flow prices the ficha from, and it
 * is on its own the whole discriminator: the admission queries
 * ({@code PaymentConceptQueryAdapter},
 * {@code AdmissionPaymentRepositoryAdapter},
 * {@code ProgramAdmissionConfigJpaRepository}) look up an {@code ACTIVE}
 * {@code ADMISSION} concept belonging to the program, expecting exactly one.
 * They deliberately do not also require {@code is_tuition} — that flag marks a
 * semester quota and says nothing about whether a concept is the admission
 * ticket, so using it here meant the catalog had to light a "cuota
 * cuatrimestral" switch just to sell an admission. Before {@code ADMISSION}
 * existed the flow borrowed {@link #ENROLLMENT}, which conflated "the admission
 * fee" with "the semester enrollment quota" and made the admission vocabulary
 * unreachable from the catalog UI.
 *
 * <p>{@link #ENROLLMENT} and {@link #REINSCRIPTION} remain for the semester
 * quotas, which is what {@code isTuition} is for.
 */
public enum PaymentConceptType {
	ADMISSION,
	ENROLLMENT,
	REINSCRIPTION,
	EXTRAORDINARY,
	DOCUMENT,
	OTHER
}
