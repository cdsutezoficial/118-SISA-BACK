package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The saldo a favor is of the person, not of the candidate record (D9): a
 * second ficha of the same person is a new candidate and must see the saldo
 * the first one left. Real DB, because the matching is JPQL across
 * {@code payment_credit} and {@code candidate}.
 *
 * <h2>Warning: this class recreates the schema it points at</h2>
 * Run it only with {@code DB_URL} pointing at {@code sisa_it}.
 */
@DataJpaTest
@Import(PaymentCreditRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentCreditPersonQueryIT {

	@Autowired
	private PaymentCreditRepositoryAdapter credits;

	@Autowired
	private FinanceCandidateJpaRepository candidates;

	private final UUID person = UUID.randomUUID();

	@Test
	void aNewFichaOfTheSamePersonSeesTheSaldoOfTheFirst() {
		Candidate first = candidate(person);
		Candidate second = candidate(person);
		Candidate stranger = candidate(UUID.randomUUID());
		PaymentCredit saldo = credits.save(credit(first, "250.00", "2026-10-01T00:00:00Z"));
		credits.save(credit(stranger, "99.00", "2026-10-02T00:00:00Z"));

		assertThat(saldo.getPersonId()).isEqualTo(person);
		assertThat(credits.findAvailableByCandidateId(second.getId())).extracting(PaymentCredit::getId)
				.containsExactly(saldo.getId());
		assertThat(credits.findByCandidateId(second.getId())).extracting(PaymentCredit::getId)
				.containsExactly(saldo.getId());
	}

	@Test
	void aSpentSaldoIsListedButNotAvailableAndOldestComesFirst() {
		Candidate first = candidate(person);
		Candidate second = candidate(person);
		PaymentCredit spent = credits.save(credit(first, "100.00", "2026-10-01T00:00:00Z"));
		spent.applyTo(UUID.randomUUID());
		credits.save(spent);
		PaymentCredit older = credits.save(credit(first, "50.00", "2026-10-02T00:00:00Z"));
		PaymentCredit newer = credits.save(credit(second, "70.00", "2026-10-03T00:00:00Z"));

		assertThat(credits.findAvailableByCandidateId(first.getId())).extracting(PaymentCredit::getId)
				.containsExactly(older.getId(), newer.getId());
		assertThat(credits.findByCandidateId(second.getId())).hasSize(3);
	}

	private Candidate candidate(UUID personId) {
		return candidates.save(new Candidate(personId, UUID.randomUUID(), "ADM-" + UUID.randomUUID(), false, true,
				null));
	}

	private static PaymentCredit credit(Candidate owner, String amount, String createdAt) {
		return new PaymentCredit(owner.getId(), null, UUID.randomUUID(), null, new BigDecimal(amount),
				Instant.parse(createdAt));
	}
}
