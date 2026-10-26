package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Read-only-by-usage Spring Data lookup over {@code admission}'s
 * {@code Candidate}, backing {@link AnchorQueryAdapter}. {@code finance}
 * navigates a payment's {@code candidateId} anchor to the person behind it
 * (name/CURP for statements) and validates the anchor before freezing an
 * adeudo; it never creates or edits a candidate.
 *
 * <p>Deliberately a finance-owned interface over the {@code candidate} table
 * rather than importing admission's port — same rationale every module keeps
 * for its cross-context reads.
 */
public interface FinanceCandidateJpaRepository extends JpaRepository<Candidate, UUID> {
}
