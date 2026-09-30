package mx.edu.utez.sisa.admission.infrastructure.persistence;

import java.time.LocalDate;
import java.util.UUID;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The one definition of "a quota slot is taken", shared by the two callers that
 * need it and deliberately not written twice.
 *
 * <p>These are the two callers: the career dropdown, which asks how full a
 * program is, and {@code CheckoutSlotClaimer}, which asks whether there is room
 * left. They must agree exactly — if the dropdown showed a career as available
 * using a different rule than the claim enforced, the user would be offered a
 * career and then refused at the last step, which is the user-visible version of
 * the original overshoot bug. One JPQL string behind both is what stops that
 * drift, and {@code ProgramAdmissionConfigOptionsQueryIT#dropdownAndClaimerAgreeOnWhatOccupiesASlot}
 * is what keeps the promise honest.
 *
 * <p>Implemented as a shared superinterface rather than as one method with a
 * nullable "exclude this candidate" parameter. The nullable version was rejected
 * on purpose: a null parameter silently changes what is being counted, and a
 * caller that forgot to branch would get a plausible number instead of an error.
 * Here both variants are named for what they do, so the compiler-visible
 * difference is the argument list.
 *
 * <p>Both are keyed by {@code admissionConfigId}, which is what the catalog's
 * {@code findOpenOfferedOptions} subquery has always counted. Before this was fixed
 * these two counted by {@code programId} while the catalog counted by config, and
 * the two answers only ever agreed for programs sold in a single period — see
 * {@code ProgramAdmissionConfigOptionsQueryIT}.
 */
interface AdmissionPaymentOccupancyQueries {

	/**
	 * Occupied slots of one admission config's quota, counted the way the quota is
	 * enforced: a ficha that PAID, or a PENDING ficha that already claimed its
	 * slot at checkout.
	 *
	 * <p>The second clause is the fix for the overshoot. The count has to include
	 * in-flight checkouts because the count is what stops the last-plus-one
	 * applicant, and it is evaluated at the one moment a refusal is still free:
	 * before Evo has been asked to take the money.
	 *
	 * <p>The {@code EXISTS} subquery is the expiry rule, and it is what keeps this
	 * design free of scheduled cleanup. A claim only holds while the admission
	 * concept can still be paid; once {@code available_until} has passed, a PENDING
	 * ficha nobody paid for drops out of the count on its own, because the count is
	 * a function of stored data rather than of a number someone has to decrement.
	 * It mirrors {@code PaymentConceptLookupJpaRepository#findActiveTuitionForProgram}
	 * so "still payable" means the same thing in both places — same type, same
	 * program membership, same window, and likewise no {@code is_tuition}: a claim
	 * held against an admission concept outlives it exactly when the concept does,
	 * whether or not the catalog also calls it a cuota cuatrimestral.
	 *
	 * <p><b>Keyed by config, not by program</b> — this was the drift that let the
	 * picker and the checkout disagree. Counting {@code cfg.programId} summed the
	 * fichas of every period the program had ever been sold in, so a program whose
	 * 2026-1 cycle was full blocked its own 2027-1 cycle at checkout while the
	 * picker, which counts per config, happily offered it. See
	 * {@code ProgramAdmissionConfigOptionsQueryIT#aFullOldCycleDoesNotBlockTheSame
	 * ProgramsNewCycle}.
	 *
	 * <p>The config's own {@code programId} is still read inside the {@code EXISTS}
	 * — the price ladder is defined per program, not per config, so "is there still
	 * a payable admission price for this" is a program-level question. Keying the
	 * <em>quota</em> by config does not make the <em>pricing</em> per config, and
	 * the subquery keeps reading it the way
	 * {@code ProgramAdmissionConfigJpaRepository#findOpenOfferedOptions} does.
	 */
	@Query("""
			SELECT COUNT(pay)
			FROM AdmissionPayment pay
			JOIN Candidate cand ON cand.id = pay.candidateId
			JOIN ProgramAdmissionConfig cfg ON cfg.id = cand.admissionConfigId
			WHERE cand.admissionConfigId = :admissionConfigId
			  AND (pay.paymentStatus = :paid
			       OR (pay.paymentStatus = :pending
			           AND pay.checkoutClaimedAt IS NOT NULL
			           AND EXISTS (
			                SELECT c FROM PaymentConcept c
			                WHERE c.status = :conceptStatus
			                  AND c.type = :conceptType
			                  AND EXISTS (
			                      SELECT r FROM PaymentRate r
			                      WHERE r.conceptId = c.id
			                        AND r.periodId IS NULL
			                        AND r.validFrom <= :onDate
			                        AND (r.validTo IS NULL OR r.validTo >= :onDate)
			                        AND (r.programId = cfg.programId
			                             OR (r.programId IS NULL AND r.level = (
			                                  SELECT p.level FROM AcademicProgram p WHERE p.id = cfg.programId))
			                             OR (r.programId IS NULL AND r.level IS NULL))
			                      )
			                  AND (c.availableUntil IS NULL OR c.availableUntil >= :onDate)
			           )))
			""")
	long countOccupiedByConfigId(@Param("admissionConfigId") UUID admissionConfigId,
			@Param("paid") AdmissionPaymentStatus paid, @Param("pending") AdmissionPaymentStatus pending,
			@Param("conceptStatus") PaymentConceptStatus conceptStatus,
			@Param("conceptType") PaymentConceptType conceptType, @Param("onDate") LocalDate onDate);

	/**
	 * The same count, ignoring the requesting candidate's own ficha.
	 *
	 * <p>This is the variant the claim itself must use, and the difference is a
	 * real user-facing bug if it is missed. A candidate who starts a checkout, gets
	 * distracted, and clicks "pay" again already holds a claim — and their own claim
	 * is inside the count. On a career that has just sold its last slot,
	 * {@code occupied >= max} would be true and they would be refused a retry for a
	 * slot that is <em>theirs</em>. Excluding themselves makes the comparison mean
	 * what it is meant to mean: "is there room for <em>one more</em> ficha besides
	 * the one I already hold".
	 *
	 * <p>It is also correct for a candidate with no prior claim, because such a
	 * ficha is not in the count either way — a PENDING payment without
	 * {@code checkout_claimed_at} occupies nothing.
	 */
	@Query("""
			SELECT COUNT(pay)
			FROM AdmissionPayment pay
			JOIN Candidate cand ON cand.id = pay.candidateId
			JOIN ProgramAdmissionConfig cfg ON cfg.id = cand.admissionConfigId
			WHERE cand.admissionConfigId = :admissionConfigId
			  AND pay.candidateId <> :candidateId
			  AND (pay.paymentStatus = :paid
			       OR (pay.paymentStatus = :pending
			           AND pay.checkoutClaimedAt IS NOT NULL
			           AND EXISTS (
			                SELECT c FROM PaymentConcept c
			                WHERE c.status = :conceptStatus
			                  AND c.type = :conceptType
			                  AND EXISTS (
			                      SELECT r FROM PaymentRate r
			                      WHERE r.conceptId = c.id
			                        AND r.periodId IS NULL
			                        AND r.validFrom <= :onDate
			                        AND (r.validTo IS NULL OR r.validTo >= :onDate)
			                        AND (r.programId = cfg.programId
			                             OR (r.programId IS NULL AND r.level = (
			                                  SELECT p.level FROM AcademicProgram p WHERE p.id = cfg.programId))
			                             OR (r.programId IS NULL AND r.level IS NULL))
			                      )
			                  AND (c.availableUntil IS NULL OR c.availableUntil >= :onDate)
			           )))
			""")
	long countOccupiedByConfigIdExcludingCandidate(@Param("admissionConfigId") UUID admissionConfigId,
			@Param("candidateId") UUID candidateId, @Param("paid") AdmissionPaymentStatus paid,
			@Param("pending") AdmissionPaymentStatus pending,
			@Param("conceptStatus") PaymentConceptStatus conceptStatus,
			@Param("conceptType") PaymentConceptType conceptType, @Param("onDate") LocalDate onDate);
}
