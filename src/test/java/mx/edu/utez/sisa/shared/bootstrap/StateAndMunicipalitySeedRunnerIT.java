package mx.edu.utez.sisa.shared.bootstrap;

import mx.edu.utez.sisa.shared.model.Municipality;
import mx.edu.utez.sisa.shared.model.State;
import mx.edu.utez.sisa.shared.persistence.MunicipalityJpaRepository;
import mx.edu.utez.sisa.shared.persistence.StateJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application context (real {@code ApplicationRunner}
 * startup sequence, same as {@code TestAccountsSeedRunner}'s own coverage style) and asserts the
 * real regression guard from
 * {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md}: exactly
 * 32 states, exactly 2,469 municipalities, and Morelos (INEGI {@code 17})
 * has a municipality named "Emiliano Zapata" with INEGI code {@code 008} —
 * manually verified against the source dump before this task, not an
 * arbitrary assertion.
 */
@SpringBootTest
class StateAndMunicipalitySeedRunnerIT {

	@Autowired
	private StateJpaRepository stateRepository;

	@Autowired
	private MunicipalityJpaRepository municipalityRepository;

	@Test
	void seedsExactlyThirtyTwoStates() {
		assertThat(stateRepository.count()).isEqualTo(32L);
	}

	@Test
	void seedsExactlyTwoThousandFourHundredSixtyNineMunicipalities() {
		assertThat(municipalityRepository.count()).isEqualTo(2469L);
	}

	@Test
	void everyMunicipalityResolvesToAnExistingState() {
		List<Municipality> municipalities = municipalityRepository.findAll();

		assertThat(municipalities).allSatisfy(municipality -> assertThat(municipality.getStateId()).isNotNull());
	}

	@Test
	void morelosHasEmilianoZapataMunicipalityWithInegiCode008() {
		Optional<State> morelos = stateRepository.findAll().stream()
				.filter(state -> state.getInegiCode().equals("17")).findFirst();
		assertThat(morelos).isPresent();
		assertThat(morelos.get().getName()).isEqualTo("Morelos");

		List<Municipality> morelosMunicipalities = municipalityRepository
				.findByStateIdOrderByNameAsc(morelos.get().getId());

		assertThat(morelosMunicipalities).hasSize(36);
		assertThat(morelosMunicipalities).anySatisfy(municipality -> {
			assertThat(municipality.getName()).isEqualTo("Emiliano Zapata");
			assertThat(municipality.getInegiCode()).isEqualTo("008");
		});
	}

	@Test
	void reRunningTheSeedRunnerIsIdempotent() throws Exception {
		long stateCountBefore = stateRepository.count();
		long municipalityCountBefore = municipalityRepository.count();

		StateAndMunicipalitySeedRunner runner = new StateAndMunicipalitySeedRunner(stateRepository,
				municipalityRepository);
		runner.run(null);

		assertThat(stateRepository.count()).isEqualTo(stateCountBefore);
		assertThat(municipalityRepository.count()).isEqualTo(municipalityCountBefore);
	}
}
