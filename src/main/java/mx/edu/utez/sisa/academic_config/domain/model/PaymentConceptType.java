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
 * <p>{@link #PERIODIC_QUOTA} is the recurring per-term charge — "colegiatura".
 * It carries {@code levelNumber} (the {@code PlanLevel.levelNumber} it applies
 * to), which is what a previously free-standing {@code isTuition} boolean used
 * to say. The flag was retired for two reasons: it made the admission flow
 * light "cuota cuatrimestral" just to sell a ficha (see
 * {@code PaymentConceptLookupJpaRepository}'s "No {@code is_tuition} predicate"
 * note), and it left "which semester does this charge apply to" unanswerable,
 * because a single recurring quota is a different price at first and second
 * year. Folding the meaning into a type keeps one axis instead of a type plus a
 * flag that half-overlapped it.
 *
 * <p>At most one ACTIVE {@link #PERIODIC_QUOTA} concept may exist per
 * {@code levelNumber}, and it must price every ACTIVE program — otherwise a
 * student's tuition would be either unpriceable or ambiguous.
 *
 * <p>{@link #ENROLLMENT} and {@link #REINSCRIPTION} are now only the
 * administrative enrollment/re-enrollment charge, paid once per cycle, which is
 * a different thing from the recurring quota they used to double for.
 */
public enum PaymentConceptType {
	ADMISSION,
	ENROLLMENT,
	REINSCRIPTION,
	PERIODIC_QUOTA,
	EXTRAORDINARY,
	DOCUMENT,
	OTHER;

	/**
	 * Whether this type requires {@code PaymentConcept.levelNumber}. The
	 * invariant is bidirectional on purpose: the level is meaningless without
	 * the recurring quota, and a recurring quota without a level has no price
	 * to look up.
	 */
	public boolean requiresLevelNumber() {
		return this == PERIODIC_QUOTA;
	}
}
