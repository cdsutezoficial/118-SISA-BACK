package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.ProgramSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.ProgramSearchPage;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB (H2) coverage for {@link AcademicProgramRepositoryAdapter#search}
 * and the dual uniqueness DB constraints — unique {@code code} and composite
 * unique {@code (offer_name, modality)} (design.md — Testing Strategy:
 * "@DataJpaTest covering dual-uniqueness constraints + divisionId/status/
 * search filters"), mirroring {@code AcademicDivisionRepositoryAdapterSearchIT}'s
 * style.
 */
@DataJpaTest
@Import(AcademicProgramRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AcademicProgramRepositoryAdapterSearchIT {

	@Autowired
	private AcademicProgramRepositoryAdapter adapter;

	@Autowired
	private AcademicProgramJpaRepository jpaRepository;

	@Test
	void duplicateCodeViolatesUniqueConstraintAtDbLevel() {
		UUID divisionId = UUID.randomUUID();
		jpaRepository.saveAndFlush(
				newProgram(divisionId, "Ingeniería en Software", "Ingeniería en Software", "ISC-01", ProgramModality.PRESENCIAL));

		AcademicProgram duplicate = newProgram(divisionId, "Otro Programa", "Otro Programa Oferta", "ISC-01",
				ProgramModality.MIXTA);

		assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void duplicateOfferNameAndModalityViolatesUniqueConstraintAtDbLevel() {
		UUID divisionId = UUID.randomUUID();
		jpaRepository.saveAndFlush(
				newProgram(divisionId, "Ingeniería en Software", "Ingeniería en Software", "ISC-01", ProgramModality.PRESENCIAL));

		AcademicProgram duplicate = newProgram(divisionId, "Otro Nombre Interno", "Ingeniería en Software", "ISC-02",
				ProgramModality.PRESENCIAL);

		assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameOfferNameWithDifferentModalityDoesNotViolateUniqueConstraint() {
		UUID divisionId = UUID.randomUUID();
		jpaRepository.saveAndFlush(
				newProgram(divisionId, "Ingeniería en Software", "Ingeniería en Software", "ISC-01", ProgramModality.PRESENCIAL));

		AcademicProgram distinct = newProgram(divisionId, "Ingeniería en Software Mixta", "Ingeniería en Software",
				"ISC-02", ProgramModality.MIXTA);

		AcademicProgram saved = jpaRepository.saveAndFlush(distinct);

		assertThat(saved.getId()).isNotNull();
	}

	@Test
	void divisionIdFilterMatchesOnlyProgramsBelongingToThatDivision() {
		UUID divisionA = UUID.randomUUID();
		UUID divisionB = UUID.randomUUID();
		jpaRepository.save(newProgram(divisionA, "Programa A", "Oferta A", "COD-A", ProgramModality.PRESENCIAL));
		jpaRepository.save(newProgram(divisionB, "Programa B", "Oferta B", "COD-B", ProgramModality.PRESENCIAL));

		ProgramSearchPage page = adapter.search(new ProgramSearchCriteria(divisionA, null, null, 0, 20));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.content().get(0).getCode()).isEqualTo("COD-A");
	}

	@Test
	void statusFilterMatchesExactStatus() {
		UUID divisionId = UUID.randomUUID();
		AcademicProgram active = jpaRepository
				.save(newProgram(divisionId, "Activo", "Oferta Activa", "ACT-01", ProgramModality.PRESENCIAL));
		AcademicProgram inactive = jpaRepository
				.save(newProgram(divisionId, "Inactivo", "Oferta Inactiva", "INA-01", ProgramModality.PRESENCIAL));
		inactive.deactivate();
		jpaRepository.save(inactive);

		ProgramSearchPage page = adapter.search(new ProgramSearchCriteria(null, ProgramStatus.INACTIVE, null, 0, 20));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.content().get(0).getCode()).isEqualTo("INA-01");
		assertThat(active).isNotNull();
	}

	@Test
	void searchMatchesNameOfferNameOrCodeCaseInsensitively() {
		UUID divisionId = UUID.randomUUID();
		jpaRepository.save(
				newProgram(divisionId, "Ingeniería en Software", "Software Engineering", "ISW-01", ProgramModality.PRESENCIAL));
		jpaRepository
				.save(newProgram(divisionId, "Diseño Gráfico", "Graphic Design", "DGR-01", ProgramModality.PRESENCIAL));

		ProgramSearchPage byOfferNameFragment = adapter.search(new ProgramSearchCriteria(null, null, "software", 0, 20));
		ProgramSearchPage byCodeFragment = adapter.search(new ProgramSearchCriteria(null, null, "dgr", 0, 20));

		assertThat(byOfferNameFragment.totalElements()).isEqualTo(1L);
		assertThat(byOfferNameFragment.content().get(0).getCode()).isEqualTo("ISW-01");
		assertThat(byCodeFragment.totalElements()).isEqualTo(1L);
		assertThat(byCodeFragment.content().get(0).getCode()).isEqualTo("DGR-01");
	}

	@Test
	void resultsAreSortedByNameAscending() {
		UUID divisionId = UUID.randomUUID();
		jpaRepository.save(newProgram(divisionId, "Zootecnia", "Oferta Z", "ZOO-01", ProgramModality.PRESENCIAL));
		jpaRepository.save(newProgram(divisionId, "Agronomía", "Oferta A", "AGR-01", ProgramModality.PRESENCIAL));
		jpaRepository.save(newProgram(divisionId, "Medicina", "Oferta M", "MED-01", ProgramModality.PRESENCIAL));

		ProgramSearchPage page = adapter.search(new ProgramSearchCriteria(null, null, null, 0, 20));

		assertThat(page.content()).extracting(AcademicProgram::getName)
				.containsExactly("Agronomía", "Medicina", "Zootecnia");
	}

	private static AcademicProgram newProgram(UUID divisionId, String name, String offerName, String code,
			ProgramModality modality) {
		return new AcademicProgram(divisionId, name, offerName, code, AcademicLevel.INGENIERIA, modality, null,
				"Description for " + name);
	}
}
