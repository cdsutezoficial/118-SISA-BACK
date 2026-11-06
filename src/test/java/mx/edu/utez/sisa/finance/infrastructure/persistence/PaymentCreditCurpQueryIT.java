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
 * the first one left. The person is the CURP, which does not change between
 * fichas. Real DB, because the matching is JPQL across {@code payment_credit}
 * and {@code candidate}.
 *
 * <h2>Warning: this class recreates the schema it points at</h2>
 * Run it only with {@code DB_URL} pointing at {@code sisa_it}.
 */
@DataJpaTest
@Import(PaymentCreditRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentCreditCurpQueryIT {

	@Autowired
	private PaymentCreditRepositoryAdapter credits;

	@Autowired
	private FinanceCandidateJpaRepository candidates;

	private final String curp = "PEGJ" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();

	@Test
	void aNewFichaOfTheSamePersonSeesTheSaldoOfTheFirst() {
		Candidate first = candidate(curp);
		Candidate second = candidate(curp);
		Candidate stranger = candidate("OTRA" + curp.substring(4));
		PaymentCredit saldo = credits.save(credit(first, "250.00", "2026-10-01T00:00:00Z"));
		credits.save(credit(stranger, "99.00", "2026-10-02T00:00:00Z"));

		assertThat(credits.findAvailableByCandidateId(second.getId())).extracting(PaymentCredit::getId)
				.containsExactly(saldo.getId());
		assertThat(credits.findByCandidateId(second.getId())).extracting(PaymentCredit::getId)
				.containsExactly(saldo.getId());
	}

	@Test
	void aSpentSaldoIsListedButNotAvailableAndOldestComesFirst() {
		Candidate first = candidate(curp);
		Candidate second = candidate(curp);
		PaymentCredit spent = credits.save(credit(first, "100.00", "2026-10-01T00:00:00Z"));
		spent.applyTo(UUID.randomUUID());
		credits.save(spent);
		PaymentCredit older = credits.save(credit(first, "50.00", "2026-10-02T00:00:00Z"));
		PaymentCredit newer = credits.save(credit(second, "70.00", "2026-10-03T00:00:00Z"));

		assertThat(credits.findAvailableByCandidateId(first.getId())).extracting(PaymentCredit::getId)
				.containsExactly(older.getId(), newer.getId());
		assertThat(credits.findByCandidateId(second.getId())).hasSize(3);
	}

	private Candidate candidate(String curp) {
		Candidate candidate = new Candidate(UUID.randomUUID(), "ADM-" + UUID.randomUUID(), false, true, null);
		candidate.setCurp(curp);
		candidate.setFirstName("Prueba");
		candidate.setLastName1("Saldo");
		return candidates.save(candidate);
	}

	private static PaymentCredit credit(Candidate owner, String amount, String createdAt) {
		return new PaymentCredit(owner.getId(), null, UUID.randomUUID(), null, new BigDecimal(amount),
				Instant.parse(createdAt));
	}
}
