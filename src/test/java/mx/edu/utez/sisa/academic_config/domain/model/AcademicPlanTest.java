package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateLevelNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateSubjectCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelHasSubjectsException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelInUseException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.SubjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicPlanTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		AcademicPlan plan = newPlan();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void deactivate_transitionsActiveToInactive() {
		AcademicPlan plan = newPlan();

		plan.deactivate();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentWhenAlreadyInactive() {
		AcademicPlan plan = newPlan();
		plan.deactivate();

		plan.deactivate();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.INACTIVE);
	}

	@Test
	void activate_transitionsInactiveToActive() {
		AcademicPlan plan = newPlan();
		plan.deactivate();

		plan.activate();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentWhenAlreadyActive() {
		AcademicPlan plan = newPlan();

		plan.activate();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void updateDetails_changesFieldsButNotStatus() {
		AcademicPlan plan = newPlan();
		plan.deactivate();
		UUID levelId = UUID.randomUUID();

		plan.updateDetails("2023-A", "Enero 2023", "TIT-002", LocalDate.of(2023, 1, 1), 7,
				BigDecimal.valueOf(7.0), 2, true, levelId);

		assertThat(plan.getVersion()).isEqualTo("2023-A");
		assertThat(plan.getValidityPeriod()).isEqualTo("Enero 2023");
		assertThat(plan.getTitulationKey()).isEqualTo("TIT-002");
		assertThat(plan.getEffectiveFrom()).isEqualTo(LocalDate.of(2023, 1, 1));
		assertThat(plan.getTotalLevels()).isEqualTo(7);
		assertThat(plan.getMinPassingGrade()).isEqualByComparingTo(BigDecimal.valueOf(7.0));
		assertThat(plan.getMaxExtraordinaryExamsPerPeriod()).isEqualTo(2);
		assertThat(plan.isRequiresSocialService()).isTrue();
		assertThat(plan.getSocialServiceMinLevelId()).isEqualTo(levelId);
		assertThat(plan.getStatus()).isEqualTo(PlanStatus.INACTIVE);
	}

	@Test
	void addLevel_addsLevelToPlan() {
		AcademicPlan plan = newPlan();

		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, "Primer cuatrimestre");

		assertThat(plan.getLevels()).containsExactly(level);
		assertThat(level.getLevelNumber()).isEqualTo(1);
		assertThat(level.getType()).isEqualTo(PlanLevelType.REGULAR);
		assertThat(level.getDescription()).isEqualTo("Primer cuatrimestre");
	}

	@Test
	void addLevel_rejectsDuplicateLevelNumberWithinTheSamePlan() {
		AcademicPlan plan = newPlan();
		plan.addLevel(3, PlanLevelType.REGULAR, null);

		assertThatThrownBy(() -> plan.addLevel(3, PlanLevelType.INTERNSHIP, null))
				.isInstanceOf(DuplicateLevelNumberException.class);
	}

	@Test
	void updateLevel_rejectsUnknownLevelId() {
		AcademicPlan plan = newPlan();
		UUID unknownLevelId = UUID.randomUUID();

		assertThatThrownBy(() -> plan.updateLevel(unknownLevelId, 1, PlanLevelType.REGULAR, null))
				.isInstanceOf(PlanLevelNotFoundException.class);
	}

	@Test
	void updateLevel_rejectsDuplicateLevelNumberAgainstAnotherLevel() {
		AcademicPlan plan = newPlan();
		plan.addLevel(1, PlanLevelType.REGULAR, null);
		PlanLevel levelTwo = plan.addLevel(2, PlanLevelType.REGULAR, null);
		UUID levelTwoId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelTwo, "id", levelTwoId);

		assertThatThrownBy(() -> plan.updateLevel(levelTwoId, 1, PlanLevelType.REGULAR, null))
				.isInstanceOf(DuplicateLevelNumberException.class);
	}

	@Test
	void removeLevel_succeedsWhenEmptyAndNotReferencedBySocialService() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);

		plan.removeLevel(levelId);

		assertThat(plan.getLevels()).isEmpty();
	}

	@Test
	void removeLevel_rejectsWhenLevelStillHasSubjects() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true,
				UUID.randomUUID());

		assertThatThrownBy(() -> plan.removeLevel(levelId)).isInstanceOf(PlanLevelHasSubjectsException.class);
	}

	@Test
	void removeLevel_rejectsWhenLevelIsSocialServiceMinLevel() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		ReflectionTestUtils.setField(plan, "socialServiceMinLevelId", levelId);

		assertThatThrownBy(() -> plan.removeLevel(levelId)).isInstanceOf(PlanLevelInUseException.class);
	}

	@Test
	void removeLevel_rejectsUnknownLevelId() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.removeLevel(UUID.randomUUID()))
				.isInstanceOf(PlanLevelNotFoundException.class);
	}

	@Test
	void addSubject_addsSubjectUnderTheGivenLevel() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		UUID classificationId = UUID.randomUUID();

		Subject subject = plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true,
				classificationId);

		assertThat(level.getSubjects()).containsExactly(subject);
		assertThat(subject.getCode()).isEqualTo("MAT101");
		assertThat(subject.getClassificationId()).isEqualTo(classificationId);
		assertThat(subject.isRetakeable()).isTrue();
	}

	@Test
	void addSubject_rejectsDuplicateCodeWithinTheSamePlan() {
		AcademicPlan plan = newPlan();
		PlanLevel levelOne = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelOneId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelOne, "id", levelOneId);
		PlanLevel levelTwo = plan.addLevel(2, PlanLevelType.REGULAR, null);
		UUID levelTwoId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelTwo, "id", levelTwoId);
		plan.addSubject(levelOneId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID());

		assertThatThrownBy(() -> plan.addSubject(levelTwoId, "MAT101", "Otra materia", 5, 4, 3, 1, SubjectType.CORE,
				true, UUID.randomUUID())).isInstanceOf(DuplicateSubjectCodeException.class);
	}

	@Test
	void addSubject_rejectsLevelIdBelongingToAnotherPlan() {
		AcademicPlan planA = newPlan();
		AcademicPlan planB = newPlan();
		PlanLevel levelOfB = planB.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelOfBId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelOfB, "id", levelOfBId);

		assertThatThrownBy(() -> planA.addSubject(levelOfBId, "MAT101", "Matematicas I", 5, 4, 3, 1,
				SubjectType.CORE, true, UUID.randomUUID())).isInstanceOf(PlanLevelNotFoundException.class);
	}

	@Test
	void updateSubject_rejectsUnknownSubjectId() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.updateSubject(UUID.randomUUID(), "MAT101", "Matematicas I", 5, 4, 3, 1,
				SubjectType.CORE, true, UUID.randomUUID())).isInstanceOf(SubjectNotFoundException.class);
	}

	@Test
	void updateSubject_rejectsDuplicateCodeAgainstAnotherSubject() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID());
		Subject subjectTwo = plan.addSubject(levelId, "MAT102", "Matematicas II", 5, 4, 3, 2, SubjectType.CORE, true,
				UUID.randomUUID());
		UUID subjectTwoId = UUID.randomUUID();
		ReflectionTestUtils.setField(subjectTwo, "id", subjectTwoId);

		assertThatThrownBy(() -> plan.updateSubject(subjectTwoId, "MAT101", "Matematicas II", 5, 4, 3, 2,
				SubjectType.CORE, true, UUID.randomUUID())).isInstanceOf(DuplicateSubjectCodeException.class);
	}

	@Test
	void removeSubject_removesItFromItsLevel() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		Subject subject = plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true,
				UUID.randomUUID());
		UUID subjectId = UUID.randomUUID();
		ReflectionTestUtils.setField(subject, "id", subjectId);

		plan.removeSubject(subjectId);

		assertThat(level.getSubjects()).isEmpty();
	}

	@Test
	void removeSubject_rejectsUnknownSubjectId() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.removeSubject(UUID.randomUUID())).isInstanceOf(SubjectNotFoundException.class);
	}

	@Test
	void hasLevel_trueForItsOwnLevelAndFalseForAnUnknownId() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);

		assertThat(plan.hasLevel(levelId)).isTrue();
		assertThat(plan.hasLevel(UUID.randomUUID())).isFalse();
	}

	private AcademicPlan newPlan() {
		return new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1),
				6, BigDecimal.valueOf(6.0), 3, false, null);
	}
}
