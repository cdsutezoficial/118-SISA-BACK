package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository.PlanSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository.PlanSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB (H2) coverage for {@link AcademicPlanRepositoryAdapter#search}, the
 * unique {@code (program_id, version)} DB constraint, and cascade
 * persistence of the {@code PlanLevel}/{@code Subject} child graph
 * (design.md — Testing Strategy: "@DataJpaTest covering unique
 * (program_id, version) DB constraint + programId/status/search filters +
 * cascade persists levels/subjects on save"), mirroring
 * {@code AcademicProgramRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(AcademicPlanRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AcademicPlanRepositoryAdapterSearchIT {

	@Autowired
	private AcademicPlanRepositoryAdapter adapter;

	@Autowired
	private AcademicPlanJpaRepository jpaRepository;

	@Test
	void duplicateProgramIdAndVersionViolatesUniqueConstraintAtDbLevel() {
		UUID programId = UUID.randomUUID();
		jpaRepository.saveAndFlush(newPlan(programId, "2022-A"));

		AcademicPlan duplicate = newPlan(programId, "2022-A");

		assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameVersionAcrossDifferentProgramsDoesNotViolateUniqueConstraint() {
		UUID programA = UUID.randomUUID();
		UUID programB = UUID.randomUUID();
		jpaRepository.saveAndFlush(newPlan(programA, "2022-A"));

		AcademicPlan distinct = newPlan(programB, "2022-A");

		AcademicPlan saved = jpaRepository.saveAndFlush(distinct);

		assertThat(saved.getId()).isNotNull();
	}

	@Test
	void programIdFilterMatchesOnlyPlansBelongingToThatProgram() {
		UUID programA = UUID.randomUUID();
		UUID programB = UUID.randomUUID();
		jpaRepository.save(newPlan(programA, "2022-A"));
		jpaRepository.save(newPlan(programB, "2022-B"));

		PlanSearchPage page = adapter.search(new PlanSearchCriteria(programA, null, null, 0, 20));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.content().get(0).getVersion()).isEqualTo("2022-A");
	}

	@Test
	void statusFilterMatchesExactStatus() {
		UUID programId = UUID.randomUUID();
		AcademicPlan active = jpaRepository.save(newPlan(programId, "ACTIVE-01"));
		AcademicPlan inactive = jpaRepository.save(newPlan(programId, "INACTIVE-01"));
		inactive.deactivate();
		jpaRepository.save(inactive);

		PlanSearchPage page = adapter.search(new PlanSearchCriteria(null, PlanStatus.INACTIVE, null, 0, 20));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.content().get(0).getVersion()).isEqualTo("INACTIVE-01");
		assertThat(active).isNotNull();
	}

	@Test
	void searchMatchesVersionOrTitulationKeyCaseInsensitively() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newPlan(programId, "2022-A", "CLAVE-SOFTWARE"));
		jpaRepository.save(newPlan(programId, "2023-B", "CLAVE-DISENO"));

		PlanSearchPage byVersionFragment = adapter.search(new PlanSearchCriteria(null, null, "2022", 0, 20));
		PlanSearchPage byTitulationKeyFragment = adapter
				.search(new PlanSearchCriteria(null, null, "diseno", 0, 20));

		assertThat(byVersionFragment.totalElements()).isEqualTo(1L);
		assertThat(byVersionFragment.content().get(0).getVersion()).isEqualTo("2022-A");
		assertThat(byTitulationKeyFragment.totalElements()).isEqualTo(1L);
		assertThat(byTitulationKeyFragment.content().get(0).getVersion()).isEqualTo("2023-B");
	}

	@Test
	void resultsAreSortedByVersionAscending() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newPlan(programId, "2023-Z"));
		jpaRepository.save(newPlan(programId, "2021-A"));
		jpaRepository.save(newPlan(programId, "2022-M"));

		PlanSearchPage page = adapter.search(new PlanSearchCriteria(null, null, null, 0, 20));

		assertThat(page.content()).extracting(AcademicPlan::getVersion).containsExactly("2021-A", "2022-M", "2023-Z");
	}

	@Test
	void cascadePersistsLevelsAndSubjectsOnSave() {
		UUID programId = UUID.randomUUID();
		AcademicPlan plan = newPlan(programId, "2022-A");
		plan.addLevel(1, PlanLevelType.REGULAR, "Primer cuatrimestre");
		AcademicPlan savedWithLevel = jpaRepository.saveAndFlush(plan);
		UUID levelId = savedWithLevel.getLevels().get(0).getId();

		savedWithLevel.addSubject(levelId, "MAT101", "Matemáticas I", 8, 5, 2, 1, SubjectType.CORE, true,
				UUID.randomUUID());

		AcademicPlan saved = adapter.save(savedWithLevel);

		AcademicPlan reloaded = jpaRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getLevels()).hasSize(1);
		assertThat(reloaded.getLevels().get(0).getSubjects()).hasSize(1);
		assertThat(reloaded.getLevels().get(0).getSubjects().get(0).getCode()).isEqualTo("MAT101");
	}

	private static AcademicPlan newPlan(UUID programId, String version) {
		return newPlan(programId, version, "Clave " + version);
	}

	private static AcademicPlan newPlan(UUID programId, String version, String titulationKey) {
		return new AcademicPlan(programId, version, "2022-2028", titulationKey, LocalDate.of(2022, 1, 10), 9,
				new BigDecimal("7.0"), 2, false, null);
	}
}
