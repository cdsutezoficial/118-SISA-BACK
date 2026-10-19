package mx.edu.utez.sisa.shared.bootstrap;

import mx.edu.utez.sisa.shared.model.PostalCodeSettlement;
import mx.edu.utez.sisa.shared.persistence.MunicipalityJpaRepository;
import mx.edu.utez.sisa.shared.persistence.PostalCodeSettlementJpaRepository;
import mx.edu.utez.sisa.shared.persistence.StateJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the SEPOMEX postal-code seed.
 */
@SpringBootTest
class PostalCodeSettlementSeedRunnerIT {

	@MockitoBean
	private JavaMailSender mailSender;

	@Autowired
	private PostalCodeSettlementJpaRepository postalCodeSettlementRepository;

	@Autowired
	private StateJpaRepository stateRepository;

	@Autowired
	private MunicipalityJpaRepository municipalityRepository;

	@Test
	void postalCode01030SeedsBothAxotlaAndFlorida() {
		List<PostalCodeSettlement> settlements = postalCodeSettlementRepository
				.findByPostalCodeOrderBySettlementNameAsc("01030");

		assertThat(settlements).hasSize(2);
		assertThat(settlements).extracting(PostalCodeSettlement::getSettlementName)
				.containsExactly("Axotla", "Florida");
	}

	@Test
	void reRunningTheSeedRunnerIsIdempotent() throws Exception {
		long countBefore = postalCodeSettlementRepository.count();

		PostalCodeSettlementSeedRunner runner = new PostalCodeSettlementSeedRunner(postalCodeSettlementRepository,
				stateRepository, municipalityRepository);
		runner.run(null);

		assertThat(postalCodeSettlementRepository.count()).isEqualTo(countBefore);
	}
}